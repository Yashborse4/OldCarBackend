package com.carselling.oldcar.config;

import com.carselling.oldcar.service.InMemoryCacheService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Rate limiting configuration with Redis backend (for global NGINX scaling)
 * and Bucket4j in-memory fallback.
 */
@Configuration
@Slf4j
public class RateLimitingConfig {

    @Value("${app.rate-limit.enabled:true}")
    private boolean rateLimitingEnabled;

    @Value("${app.rate-limit.capacity:100}")
    private int capacity;

    @Value("${app.rate-limit.refill-tokens:10}")
    private int refillTokens;

    @Value("${app.rate-limit.refill-period-minutes:1}")
    private int refillPeriodMinutes;

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private InMemoryCacheService inMemoryCacheService;

    @Bean
    public StringRedisTemplate rateLimitRedisTemplate() {
        if (redisConnectionFactory != null) {
            return new StringRedisTemplate(redisConnectionFactory);
        }
        return null;
    }

    @Bean
    public RateLimitService rateLimitService(
            @Autowired(required = false) StringRedisTemplate rateLimitRedisTemplate) {

        boolean useRedis = redisEnabled && rateLimitRedisTemplate != null;
        if (useRedis) {
            try {
                // Test connection
                rateLimitRedisTemplate.getConnectionFactory().getConnection().ping();
                log.info(
                        "Redis is available. Using GLOBAL REST-based token bucket for Rate Limiting (NGINX cluster safe).");
                return new RedisRateLimitService(capacity, refillPeriodMinutes, rateLimitRedisTemplate,
                        rateLimitingEnabled);
            } catch (Exception e) {
                log.warn("Redis ping failed, falling back to local Bucket4j memory cache: {}", e.getMessage());
                useRedis = false;
            }
        } else {
            log.info(
                    "Redis disabled or unavailable. Using LOCAL Bucket4j memory cache. BEWARE: Local Rate Limiting Multiplier effect behind NGINX.");
        }

        return new LocalBucket4jRateLimitService(capacity, refillPeriodMinutes, rateLimitingEnabled);
    }

    public boolean isRateLimitingEnabled() {
        return rateLimitingEnabled;
    }

    public interface RateLimitService {
        boolean isAllowed(String key);

        boolean isAllowed(String key, String role);

        boolean isAllowed(String key, int tokens);

        boolean isAllowed(String key, int capacity, int refillTokens, int refillPeriod);

        long getRemainingTokens(String key);

        void reset(String key);

        void resetAll();
    }

    // Role-based capacity configs mapped here to avoid repetition
    private static int[] getRoleLimits(String role, int defaultCap, int defaultRefill) {
        if ("ADMIN".equalsIgnoreCase(role))
            return new int[] { 2000, 500 };
        if ("DEALER".equalsIgnoreCase(role))
            return new int[] { 500, 100 };
        if ("USER".equalsIgnoreCase(role))
            return new int[] { 100, 20 };
        if ("ANONYMOUS".equalsIgnoreCase(role))
            return new int[] { 20, 5 };
        return new int[] { defaultCap, defaultRefill };
    }

    /**
     * PRODUCTION-READY Redis Token Bucket implementation.
     * Prevents Local Rate Limiting Multiplier issue when running in cluster behind
     * NGINX.
     */
    public static class RedisRateLimitService implements RateLimitService {
        private final int defaultCapacity;
        private final int defaultRefillPeriodMinutes;
        private final StringRedisTemplate redisTemplate;
        private final boolean enabled;
        private final DefaultRedisScript<Long> tokenBucketScript;

        public RedisRateLimitService(int capacity, int refillPeriodMinutes, StringRedisTemplate redisTemplate,
                boolean enabled) {
            this.defaultCapacity = capacity;
            this.defaultRefillPeriodMinutes = refillPeriodMinutes;
            this.redisTemplate = redisTemplate;
            this.enabled = enabled;

            // Lua script for Atomic Token Bucket
            String script = "local key = KEYS[1]\n" +
                    "local capacity = tonumber(ARGV[1])\n" +
                    "local refillTokens = tonumber(ARGV[2])\n" +
                    "local refillPeriodSecs = tonumber(ARGV[3])\n" +
                    "local requested = tonumber(ARGV[4])\n" +
                    "local now = redis.call('TIME')[1]\n" +
                    "local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')\n" +
                    "local tokens = tonumber(bucket[1])\n" +
                    "local lastRefill = tonumber(bucket[2])\n" +
                    "if not tokens then\n" +
                    "    tokens = capacity\n" +
                    "    lastRefill = now\n" +
                    "else\n" +
                    "    local elapsed = math.max(0, now - lastRefill)\n" +
                    "    local intervals = math.floor(elapsed / refillPeriodSecs)\n" +
                    "    if intervals > 0 then\n" +
                    "        tokens = math.min(capacity, tokens + (intervals * refillTokens))\n" +
                    "        lastRefill = lastRefill + (intervals * refillPeriodSecs)\n" +
                    "    end\n" +
                    "end\n" +
                    "if tokens >= requested then\n" +
                    "    tokens = tokens - requested\n" +
                    "    redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill)\n" +
                    "    redis.call('EXPIRE', key, math.ceil(capacity / refillTokens) * refillPeriodSecs)\n" +
                    "    return tokens\n" +
                    "else\n" +
                    "    return -1\n" +
                    "end";
            this.tokenBucketScript = new DefaultRedisScript<>(script, Long.class);
        }

        @Override
        public boolean isAllowed(String key) {
            return isAllowed(key, 1);
        }

        @Override
        public boolean isAllowed(String key, String role) {
            int[] limits = getRoleLimits(role, defaultCapacity, defaultCapacity);
            return isAllowed(key, limits[0], limits[1], defaultRefillPeriodMinutes);
        }

        @Override
        public boolean isAllowed(String key, int tokens) {
            return executeLua(key, defaultCapacity, defaultCapacity, defaultRefillPeriodMinutes, tokens) >= 0;
        }

        @Override
        public boolean isAllowed(String key, int capacity, int refillTokens, int refillPeriod) {
            return executeLua(key, capacity, refillTokens, refillPeriod, 1) >= 0;
        }

        @Override
        public long getRemainingTokens(String key) {
            // Check remaining. Request 0 tokens just to update bucket and get count.
            return Math.max(0, executeLua(key, defaultCapacity, defaultCapacity, defaultRefillPeriodMinutes, 0));
        }

        @Override
        public void reset(String key) {
            if (enabled)
                redisTemplate.delete("rl:" + key);
        }

        @Override
        public void resetAll() {
            log.warn("resetAll() called on RedisRateLimitService. Ignoring as it is unsafe for global cache.");
        }

        private long executeLua(String key, int capacity, int refillTokens, int refillPeriodMinutes,
                int requestedTokens) {
            if (!enabled)
                return capacity;
            try {
                Long result = redisTemplate.execute(tokenBucketScript, Collections.singletonList("rl:" + key),
                        String.valueOf(capacity), String.valueOf(refillTokens),
                        String.valueOf(refillPeriodMinutes * 60), String.valueOf(requestedTokens));
                return result != null ? result : -1;
            } catch (Exception e) {
                log.error("Redis rate limit error for key {}: {}", key, e.getMessage());
                return capacity; // Fail open
            }
        }
    }

    /**
     * Local memory rate limiter using high-performance Bucket4j.
     * Bypass global limits in a cluster setting.
     */
    public static class LocalBucket4jRateLimitService implements RateLimitService {
        private final int defaultCapacity;
        private final int defaultRefillPeriodMinutes;
        private final boolean enabled;
        private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

        public LocalBucket4jRateLimitService(int capacity, int refillPeriodMinutes, boolean enabled) {
            this.defaultCapacity = capacity;
            this.defaultRefillPeriodMinutes = refillPeriodMinutes;
            this.enabled = enabled;
        }

        @Override
        public boolean isAllowed(String key) {
            return isAllowed(key, 1);
        }

        @Override
        public boolean isAllowed(String key, int tokens) {
            return isAllowed(key, defaultCapacity, defaultCapacity, defaultRefillPeriodMinutes, tokens);
        }

        @Override
        public boolean isAllowed(String key, String role) {
            int[] limits = getRoleLimits(role, defaultCapacity, defaultCapacity);
            return isAllowed(key, limits[0], limits[1], defaultRefillPeriodMinutes, 1);
        }

        @Override
        public boolean isAllowed(String key, int limitCapacity, int refillTokens, int refillPeriod) {
            return isAllowed(key, limitCapacity, refillTokens, refillPeriod, 1);
        }

        private boolean isAllowed(String key, int limitCapacity, int refillTokens, int refillPeriod,
                int tokensRequested) {
            if (!enabled)
                return true;
            try {
                Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(limitCapacity)
                                .refillGreedy(refillTokens, Duration.ofMinutes(refillPeriod))
                                .build())
                        .build());
                return bucket.tryConsume(tokensRequested);
            } catch (Exception e) {
                log.error("Local rate limit error for key {}: {}", key, e.getMessage());
                return true; // Fail open
            }
        }

        @Override
        public long getRemainingTokens(String key) {
            if (!enabled)
                return defaultCapacity;
            Bucket bucket = buckets.get(key);
            return bucket != null ? bucket.getAvailableTokens() : defaultCapacity;
        }

        @Override
        public void reset(String key) {
            buckets.remove(key);
        }

        @Override
        public void resetAll() {
            buckets.clear();
        }
    }
}
