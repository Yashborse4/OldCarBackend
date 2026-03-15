package com.carselling.oldcar.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import java.util.Set;

/**
 * Component that clears the Redis cache on application startup.
 * This is useful when object structures change and old serialized data
 * in Redis would cause DeserializationExceptions.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisCacheClearer {

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.clear-on-startup:true}")
    private boolean clearOnStartup;

    /**
     * Clears all keys from Redis on startup if enabled.
     */
    @PostConstruct
    public void clearCache() {
        if (!clearOnStartup) {
            log.info("Redis cache clearing on startup is disabled.");
            return;
        }

        try {
            log.info("Clearing Redis cache on startup...");
            Set<String> keys = redisTemplate.keys("*");
            if (keys != null && !keys.isEmpty()) {
                Long deletedCount = redisTemplate.delete(keys);
                log.info("Redis cache cleared. Total keys removed: {}", deletedCount);
            } else {
                log.info("Redis cache is empty. No keys to clear.");
            }
        } catch (Exception e) {
            log.error("Failed to clear Redis cache on startup: {} (This is normal if Redis is unavailable and fallback is active)", e.getMessage());
        }
    }
}
