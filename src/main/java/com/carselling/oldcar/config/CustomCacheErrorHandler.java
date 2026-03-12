package com.carselling.oldcar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Custom CacheErrorHandler that gracefully handles Redis cache errors.
 * Instead of propagating exceptions (which crash the auth filter),
 * it logs warnings and treats errors as cache misses.
 */
@Slf4j
public class CustomCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache GET error on cache '{}', key '{}': {}. Treating as cache miss.",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Cache PUT error on cache '{}', key '{}': {}. Ignoring.",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Cache EVICT error on cache '{}', key '{}': {}. Ignoring.",
                cache.getName(), key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Cache CLEAR error on cache '{}': {}. Ignoring.",
                cache.getName(), exception.getMessage());
    }
}
