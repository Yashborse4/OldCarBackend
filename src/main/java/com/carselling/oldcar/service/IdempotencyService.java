package com.carselling.oldcar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service to manage idempotency keys.
 * Uses Redis if available, falls back to InMemoryCacheService.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyService {

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    private final InMemoryCacheService inMemoryCacheService;

    private static final long DEFAULT_EXPIRATION_MINUTES = 60 * 24; // 24 hours

    /**
     * Tries to acquire a lock for the idempotency key.
     * 
     * @param key The idempotency key
     * @return true if the key is new (lock acquired), false if it already exists
     */
    public boolean lock(String key) {
        String fullKey = "idempotency:" + key;

        if (isRedisAvailable()) {
            try {
                // setIfAbsent returns true if key was set (did not exist)
                Boolean set = redisTemplate.opsForValue().setIfAbsent(fullKey, "PROCESSING", DEFAULT_EXPIRATION_MINUTES,
                        TimeUnit.MINUTES);
                return Boolean.TRUE.equals(set);
            } catch (Exception e) {
                log.error("Redis error in IdempotencyService, falling back to memory: {}", e.getMessage());
                // Fallback to memory
            }
        }

        // In-memory fallback
        Object value = inMemoryCacheService.get(fullKey);
        if (value != null) {
            return false; // Already exists
        }

        inMemoryCacheService.put(fullKey, "PROCESSING", DEFAULT_EXPIRATION_MINUTES * 60 * 1000L);
        return true;
    }

    /**
     * Marks the key as completed (optional, depending on strategy).
     * For now, we just keep the key to prevent re-submission for the duration.
     * We could update the value to "COMPLETED" or store the response.
     */
    public void complete(String key) {

        // logic to update status if needed.
        // For simple duplication prevention, just existing is enough.
    }

    /**
     * Releases the key (e.g. if processing failed).
     */
    public void release(String key) {
        String fullKey = "idempotency:" + key;

        if (isRedisAvailable()) {
            try {
                redisTemplate.delete(fullKey);
                return;
            } catch (Exception e) {
                log.error("Redis error removing key: {}", e.getMessage());
            }
        }

        inMemoryCacheService.remove(fullKey);
    }

    private boolean isRedisAvailable() {
        return redisEnabled && redisTemplate != null && redisTemplate.getConnectionFactory() != null;
    }
}
