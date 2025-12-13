package com.carselling.oldcar.config;

import com.carselling.oldcar.service.InMemoryCacheService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.Refill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Rate limiting configuration with Redis backend and in-memory fallback
 */
@Configuration
@RequiredArgsConstructor
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

    @Autowired
    private InMemoryCacheService inMemoryCacheService;

    /**
     * In-memory bucket cache for rate limiting (fallback and default)
     */
    @Bean
    public ConcurrentMap<String, Bucket> bucketCache() {
        if (redisEnabled && redisConnectionFactory != null) {
            try {
                redisConnectionFactory.getConnection().ping();
                log.info("Redis available for rate limiting, but using in-memory cache for simplicity");
            } catch (Exception e) {
                log.warn("Redis not available for rate limiting, using in-memory cache: {}", e.getMessage());
            }
        } else {
            log.info("Redis disabled or not configured, using in-memory rate limiting cache");
        }
        
        return new ConcurrentHashMap<>();
    }

    /**
     * Enhanced rate limiting service with better fallback handling
     */
    @Bean
    public RateLimitService rateLimitService() {
        return new InMemoryRateLimitService(capacity, refillTokens, refillPeriodMinutes, inMemoryCacheService, rateLimitingEnabled);
    }

    public BucketConfiguration createBucketConfiguration() {
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(capacity, Refill.greedy(refillTokens, Duration.ofMinutes(refillPeriodMinutes))))
                .build();
    }

    public boolean isRateLimitingEnabled() {
        return rateLimitingEnabled;
    }

    /**
     * Rate limiting service interface
     */
    public interface RateLimitService {
        boolean isAllowed(String key);
        boolean isAllowed(String key, int tokens);
        long getRemainingTokens(String key);
        void reset(String key);
        void resetAll();
    }

    /**
     * Enhanced in-memory rate limiting implementation
     */
    public static class InMemoryRateLimitService implements RateLimitService {
        private final int capacity;
        private final int refillTokens;
        private final int refillPeriodMinutes;
        private final InMemoryCacheService cacheService;
        private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();
        private final boolean rateLimitingEnabled;

        public InMemoryRateLimitService(int capacity, int refillTokens, int refillPeriodMinutes, InMemoryCacheService cacheService, boolean rateLimitingEnabled) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodMinutes = refillPeriodMinutes;
            this.cacheService = cacheService;
            this.rateLimitingEnabled = rateLimitingEnabled;
            log.info("InMemoryRateLimitService initialized with capacity: {}, refill: {} tokens per {} minutes, enabled: {}", 
                    capacity, refillTokens, refillPeriodMinutes, rateLimitingEnabled);
        }

        @Override
        public boolean isAllowed(String key) {
            return isAllowed(key, 1);
        }

        @Override
        public boolean isAllowed(String key, int tokens) {
            if (!rateLimitingEnabled) {
                return true; // Rate limiting disabled
            }
            
            try {
                TokenBucket bucket = getBucket(key);
                boolean allowed = bucket.tryConsume(tokens);
                log.debug("Rate limit check for key '{}': {} tokens requested, allowed: {}, remaining: {}", 
                        key, tokens, allowed, bucket.getAvailableTokens());
                return allowed;
            } catch (Exception e) {
                log.error("Error during rate limit check for key '{}': {}", key, e.getMessage());
                return true; // Fail open - allow request if rate limiting fails
            }
        }

        @Override
        public long getRemainingTokens(String key) {
            try {
                TokenBucket bucket = getBucket(key);
                return bucket.getAvailableTokens();
            } catch (Exception e) {
                log.error("Error getting remaining tokens for key '{}': {}", key, e.getMessage());
                return capacity; // Return full capacity on error
            }
        }

        @Override
        public void reset(String key) {
            try {
                buckets.remove(key);
                cacheService.remove("rate_limit:" + key);
                log.debug("Reset rate limit for key: {}", key);
            } catch (Exception e) {
                log.error("Error resetting rate limit for key '{}': {}", key, e.getMessage());
            }
        }

        @Override
        public void resetAll() {
            try {
                int bucketCount = buckets.size();
                buckets.clear();
                log.info("Reset all {} rate limit buckets", bucketCount);
            } catch (Exception e) {
                log.error("Error resetting all rate limits: {}", e.getMessage());
            }
        }

        private TokenBucket getBucket(String key) {
            return buckets.computeIfAbsent(key, k -> {
                log.debug("Creating new token bucket for key: {}", k);
                return new TokenBucket(capacity, refillTokens, refillPeriodMinutes);
            });
        }

        /**
         * Thread-safe token bucket implementation
         */
        private static class TokenBucket {
            private final int capacity;
            private final int refillTokens;
            private final long refillPeriodMillis;
            private final AtomicLong tokens;
            private volatile long lastRefillTime;
            private final Object refillLock = new Object();

            public TokenBucket(int capacity, int refillTokens, int refillPeriodMinutes) {
                this.capacity = capacity;
                this.refillTokens = refillTokens;
                this.refillPeriodMillis = refillPeriodMinutes * 60 * 1000L;
                this.tokens = new AtomicLong(capacity);
                this.lastRefillTime = System.currentTimeMillis();
            }

            public boolean tryConsume(int tokensToConsume) {
                if (tokensToConsume <= 0) {
                    return true;
                }
                if (tokensToConsume > capacity) {
                    return false; // Request exceeds bucket capacity
                }
                
                refillIfNeeded();
                
                // Atomic compare-and-swap loop
                while (true) {
                    long currentTokens = tokens.get();
                    if (currentTokens < tokensToConsume) {
                        return false; // Not enough tokens
                    }
                    
                    if (tokens.compareAndSet(currentTokens, currentTokens - tokensToConsume)) {
                        return true; // Successfully consumed tokens
                    }
                    // Retry if CAS failed due to concurrent modification
                }
            }

            public long getAvailableTokens() {
                refillIfNeeded();
                return tokens.get();
            }

            private void refillIfNeeded() {
                long now = System.currentTimeMillis();
                long timeSinceLastRefill = now - lastRefillTime;
                
                if (timeSinceLastRefill >= refillPeriodMillis) {
                    synchronized (refillLock) {
                        // Double-check inside synchronized block
                        now = System.currentTimeMillis();
                        timeSinceLastRefill = now - lastRefillTime;
                        
                        if (timeSinceLastRefill >= refillPeriodMillis) {
                            long periodsElapsed = timeSinceLastRefill / refillPeriodMillis;
                            long tokensToAdd = Math.min(periodsElapsed * refillTokens, capacity);
                            
                            // Add tokens up to capacity
                            while (true) {
                                long currentTokens = tokens.get();
                                long newTokens = Math.min(currentTokens + tokensToAdd, capacity);
                                
                                if (tokens.compareAndSet(currentTokens, newTokens)) {
                                    lastRefillTime = now - (timeSinceLastRefill % refillPeriodMillis);
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
