package com.carselling.oldcar.config;

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

    @Value("${app.rate-limit.levels.anonymous.capacity:100}")
    private int anonCapacity;
    @Value("${app.rate-limit.levels.anonymous.refill-tokens:20}")
    private int anonRefill;

    @Value("${app.rate-limit.levels.user.capacity:1000}")
    private int userCapacity;
    @Value("${app.rate-limit.levels.user.refill-tokens:200}")
    private int userRefill;

    @Value("${app.rate-limit.levels.dealer.capacity:5000}")
    private int dealerCapacity;
    @Value("${app.rate-limit.levels.dealer.refill-tokens:1000}")
    private int dealerRefill;

    @Value("${app.rate-limit.levels.admin.capacity:10000}")
    private int adminCapacity;
    @Value("${app.rate-limit.levels.admin.refill-tokens:2000}")
    private int adminRefill;

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    public enum RateLimitPriority {
        CRITICAL(1.0),      // Never throttled unless system is Down
        NORMAL(0.8),        // Standard traffic
        BACKGROUND(0.5);    // Aggressively throttled during load
        
        private final double weight;
        RateLimitPriority(double weight) { this.weight = weight; }
        public double getWeight() { return weight; }
    }

    public record RoleLimitsConfig(int adminCap, int adminRefill, int dealerCap, int dealerRefill, int userCap, int userRefill, int anonCap, int anonRefill) {}

    @Bean
    public RoleLimitsConfig roleLimitsConfig() {
        return new RoleLimitsConfig(adminCapacity, adminRefill, dealerCapacity, dealerRefill, userCapacity, userRefill, anonCapacity, anonRefill);
    }

    @Bean
    public StringRedisTemplate rateLimitRedisTemplate() {
        if (redisConnectionFactory != null) {
            return new StringRedisTemplate(redisConnectionFactory);
        }
        return null;
    }

    @Bean
    public RateLimitService rateLimitService(
            @Autowired(required = false) StringRedisTemplate rateLimitRedisTemplate,
            RoleLimitsConfig roleLimits) {

        boolean useRedis = redisEnabled && rateLimitRedisTemplate != null;
        if (useRedis) {
            try {
                // Test connection
                if (rateLimitRedisTemplate != null && rateLimitRedisTemplate.getConnectionFactory() != null) {
                    rateLimitRedisTemplate.getConnectionFactory().getConnection().ping();
                } else {
                    log.warn("Redis Connection Factory is null, falling back to local Bucket4j");
                    useRedis = false;
                }
                log.info(
                        "Redis is available. Using GLOBAL REST-based token bucket for Rate Limiting (NGINX cluster safe).");
                return new RedisRateLimitService(roleLimits, capacity, refillPeriodMinutes, rateLimitRedisTemplate,
                        rateLimitingEnabled);
            } catch (Exception e) {
                log.warn("Redis ping failed, falling back to local Bucket4j memory cache: {}", e.getMessage());
                useRedis = false;
            }
        } else {
            log.info(
                    "Redis disabled or unavailable. Using LOCAL Bucket4j memory cache. BEWARE: Local Rate Limiting Multiplier effect behind NGINX.");
        }

        return new LocalBucket4jRateLimitService(roleLimits, capacity, refillPeriodMinutes, rateLimitingEnabled);
    }

    public boolean isRateLimitingEnabled() {
        return rateLimitingEnabled;
    }

    public interface RateLimitService {
        CheckResult allow(String key);

        CheckResult allow(String key, String role);

        CheckResult allow(String key, int tokens);

        CheckResult allow(String key, int capacity, int refillTokens, int refillPeriod);

        /**
         * Enhanced allow for Netflix-style adaptive rate limiting
         */
        CheckResult allow(String key, int capacity, int refillTokens, int refillPeriod, 
                         RateLimitPriority priority, double healthScore);

        boolean isAllowed(String key);

        boolean isAllowed(String key, String role);

        boolean isAllowed(String key, int tokens);

        boolean isAllowed(String key, int capacity, int refillTokens, int refillPeriod);

        long getRemainingTokens(String key);

        void reset(String key);

        void resetAll();

        /**
         * Result object to avoid double Redis calls (check + remaining)
         */
        record CheckResult(boolean allowed, long remaining, long resetTimeMs) {
            public CheckResult(boolean allowed, long remaining) {
                this(allowed, remaining, System.currentTimeMillis() + 60000);
            }
        }
    }

    // Role-based capacity configs mapped here to avoid repetition
    private static int[] getRoleLimits(RoleLimitsConfig config, String role, int defaultCap, int defaultRefill) {
        if (config == null) return new int[] { defaultCap, defaultRefill };
        if ("ADMIN".equalsIgnoreCase(role))
            return new int[] { config.adminCap(), config.adminRefill() };
        if ("DEALER".equalsIgnoreCase(role))
            return new int[] { config.dealerCap(), config.dealerRefill() };
        if ("USER".equalsIgnoreCase(role))
            return new int[] { config.userCap(), config.userRefill() };
        if ("ANONYMOUS".equalsIgnoreCase(role))
            return new int[] { config.anonCap(), config.anonRefill() };
        return new int[] { defaultCap, defaultRefill };
    }

    /**
     * PRODUCTION-READY Redis Token Bucket implementation.
     * Prevents Local Rate Limiting Multiplier issue when running in cluster behind
     * NGINX.
     */
    public static class RedisRateLimitService implements RateLimitService {
        private final RoleLimitsConfig roleLimits;
        private final int defaultCapacity;
        private final int defaultRefillPeriodMinutes;
        private final StringRedisTemplate redisTemplate;
        private final boolean enabled;
        private final DefaultRedisScript<Long> tokenBucketScript;

        public RedisRateLimitService(RoleLimitsConfig roleLimits, int capacity, int refillPeriodMinutes, StringRedisTemplate redisTemplate,
                boolean enabled) {
            this.roleLimits = roleLimits;
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
                    "-- Using TIME is safe and better than local server time in a cluster\n" +
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
        public CheckResult allow(String key) {
            return allow(key, 1);
        }

        @Override
        public CheckResult allow(String key, String role) {
            int[] limits = getRoleLimits(roleLimits, role, defaultCapacity, defaultCapacity);
            return allow(key, limits[0], limits[1], defaultRefillPeriodMinutes);
        }

        @Override
        public CheckResult allow(String key, int tokens) {
            return allow(key, defaultCapacity, defaultCapacity, defaultRefillPeriodMinutes, RateLimitPriority.NORMAL, 1.0);
        }

        @Override
        public CheckResult allow(String key, int capacity, int refillTokens, int refillPeriod) {
            return allow(key, capacity, refillTokens, refillPeriod, RateLimitPriority.NORMAL, 1.0);
        }

        @Override
        public boolean isAllowed(String key) { return allow(key).allowed(); }
        @Override
        public boolean isAllowed(String key, String role) { return allow(key, role).allowed(); }
        @Override
        public boolean isAllowed(String key, int tokens) { return allow(key, tokens).allowed(); }
        @Override
        public boolean isAllowed(String key, int capacity, int refillTokens, int refillPeriod) { return allow(key, capacity, refillTokens, refillPeriod).allowed(); }

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


        @Override
        public CheckResult allow(String key, int capacity, int refillTokens, int refillPeriod, 
                                 RateLimitPriority priority, double healthScore) {
            // Adaptive multiplier: System stress reduces capacity/refill for lower priorities
            double adaptiveMultiplier = (priority == RateLimitPriority.CRITICAL) ? 1.0 : healthScore;
            
            // Priority weight: Background tasks get further reduced
            double priorityWeight = priority.getWeight();
            
            int effectiveCapacity = Math.max(1, (int) (capacity * adaptiveMultiplier * priorityWeight));
            int effectiveRefill = Math.max(1, (int) (refillTokens * adaptiveMultiplier * priorityWeight));
            
            long result = executeLua(key, effectiveCapacity, effectiveRefill, refillPeriod, 1);
            
            // Calculate reset time (approximate)
            long resetTime = System.currentTimeMillis() + (long) refillPeriod * 60 * 1000;
            
            return new CheckResult(result >= 0, Math.max(0, result), resetTime);
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
        private final RoleLimitsConfig roleLimits;
        private final int defaultCapacity;
        private final int defaultRefillPeriodMinutes;
        private final boolean enabled;
        private final ConcurrentMap<String, Bucket> buckets = new ConcurrentHashMap<>();

        public LocalBucket4jRateLimitService(RoleLimitsConfig roleLimits, int capacity, int refillPeriodMinutes, boolean enabled) {
            this.roleLimits = roleLimits;
            this.defaultCapacity = capacity;
            this.defaultRefillPeriodMinutes = refillPeriodMinutes;
            this.enabled = enabled;
        }

        @Override
        public CheckResult allow(String key) {
            return allow(key, 1);
        }

        @Override
        public CheckResult allow(String key, int tokens) {
            return allow(key, defaultCapacity, defaultCapacity, defaultRefillPeriodMinutes, RateLimitPriority.NORMAL, 1.0);
        }

        @Override
        public CheckResult allow(String key, String role) {
            int[] limits = getRoleLimits(roleLimits, role, defaultCapacity, defaultCapacity);
            return allow(key, limits[0], limits[1], defaultRefillPeriodMinutes, RateLimitPriority.NORMAL, 1.0);
        }

        @Override
        public CheckResult allow(String key, int limitCapacity, int refillTokens, int refillPeriod) {
            return allow(key, limitCapacity, refillTokens, refillPeriod, RateLimitPriority.NORMAL, 1.0);
        }

        @Override
        public CheckResult allow(String key, int capacity, int refillTokens, int refillPeriod, 
                                 RateLimitPriority priority, double healthScore) {
            // Adaptive multiplier
            double multiplier = (priority == RateLimitPriority.CRITICAL) ? 1.0 : healthScore;
            double weight = priority.getWeight();
            
            int effectiveCapacity = Math.max(1, (int) (capacity * multiplier * weight));
            int effectiveRefill = Math.max(1, (int) (refillTokens * multiplier * weight));
            
            return allowInternal(key, effectiveCapacity, effectiveRefill, refillPeriod, 1);
        }

        private CheckResult allowInternal(String key, int limitCapacity, int refillTokens, int refillPeriod,
                int tokensRequested) {
            if (!enabled)
                return new CheckResult(true, limitCapacity);
            try {
                Bucket bucket = buckets.computeIfAbsent(key, k -> Bucket.builder()
                        .addLimit(Bandwidth.builder()
                                .capacity(limitCapacity)
                                .refillGreedy(refillTokens, Duration.ofMinutes(refillPeriod))
                                .build())
                        .build());
                boolean allowed = bucket.tryConsume(tokensRequested);
                long resetTime = System.currentTimeMillis() + (long) refillPeriod * 60 * 1000;
                return new CheckResult(allowed, bucket.getAvailableTokens(), resetTime);
            } catch (Exception e) {
                log.error("Local rate limit error for key {}: {}", key, e.getMessage());
                return new CheckResult(true, limitCapacity); // Fail open
            }
        }

        @Override
        public boolean isAllowed(String key) { return allow(key).allowed(); }
        @Override
        public boolean isAllowed(String key, String role) { return allow(key, role).allowed(); }
        @Override
        public boolean isAllowed(String key, int tokens) { return allow(key, tokens).allowed(); }
        @Override
        public boolean isAllowed(String key, int capacity, int refillTokens, int refillPeriod) { return allow(key, capacity, refillTokens, refillPeriod).allowed(); }

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
