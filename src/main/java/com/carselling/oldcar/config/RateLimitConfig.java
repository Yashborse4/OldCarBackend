package com.carselling.oldcar.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configuration for rate limiting using Token Bucket algorithm
 */
@Configuration
public class RateLimitConfig {

    private final ConcurrentHashMap<String, Bucket> cache = new ConcurrentHashMap<>();

    /**
     * Create bucket for API rate limiting
     * Default: 100 requests per minute
     */
    @Bean
    public Bucket createApiRateLimitBucket() {
        return createBucket(100, Duration.ofMinutes(1));
    }

    /**
     * Create bucket for login attempts
     * Stricter: 5 attempts per 15 minutes
     */
    public Bucket createLoginRateLimitBucket() {
        return createBucket(5, Duration.ofMinutes(15));
    }

    /**
     * Create bucket for registration attempts
     * Moderate: 3 attempts per 10 minutes
     */
    public Bucket createRegistrationRateLimitBucket() {
        return createBucket(3, Duration.ofMinutes(10));
    }

    /**
     * Create bucket for file upload
     * Conservative: 10 uploads per hour
     */
    public Bucket createFileUploadRateLimitBucket() {
        return createBucket(10, Duration.ofHours(1));
    }

    /**
     * Create bucket for email sending
     * Very conservative: 5 emails per hour
     */
    public Bucket createEmailRateLimitBucket() {
        return createBucket(5, Duration.ofHours(1));
    }

    /**
     * Get or create bucket for a specific key (typically IP address or user ID)
     */
    public Bucket resolveBucket(String key, int capacity, Duration refillPeriod) {
        return cache.computeIfAbsent(key, k -> createBucket(capacity, refillPeriod));
    }

    /**
     * Create a bucket with specified capacity and refill period
     */
    private Bucket createBucket(int capacity, Duration refillPeriod) {
        Bandwidth limit = Bandwidth.classic(capacity, Refill.intervally(capacity, refillPeriod));
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Clear cache entry for a key
     */
    public void clearBucket(String key) {
        cache.remove(key);
    }

    /**
     * Get cache size for monitoring
     */
    public int getCacheSize() {
        return cache.size();
    }
}
