package com.carselling.oldcar.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * In-memory cache fallback service for when Redis is unavailable.
 * Provides thread-safe operations with TTL support and memory management.
 */
@Service
@Slf4j
public class InMemoryCacheService {

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final Timer cleanupTimer;
    
    // Configuration
    private static final long DEFAULT_TTL_MINUTES = 60;
    private static final long CLEANUP_INTERVAL_MINUTES = 10;
    private static final int MAX_CACHE_SIZE = 10000;
    
    public InMemoryCacheService() {
        log.info("Initializing InMemoryCacheService as Redis fallback");
        
        // Schedule periodic cleanup task
        this.cleanupTimer = new Timer("InMemoryCache-Cleanup", true);
        this.cleanupTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                cleanupExpiredEntries();
            }
        }, CLEANUP_INTERVAL_MINUTES * 60 * 1000, CLEANUP_INTERVAL_MINUTES * 60 * 1000);
    }

    /**
     * Store value with default TTL
     */
    public void put(String key, Object value) {
        put(key, value, DEFAULT_TTL_MINUTES);
    }

    /**
     * Store value with specified TTL in minutes
     */
    public void put(String key, Object value, long ttlMinutes) {
        lock.writeLock().lock();
        try {
            // Check cache size limit
            if (cache.size() >= MAX_CACHE_SIZE) {
                evictLeastRecentlyUsed();
            }
            
            LocalDateTime expiryTime = LocalDateTime.now().plus(ttlMinutes, ChronoUnit.MINUTES);
            cache.put(key, new CacheEntry(value, expiryTime, LocalDateTime.now()));
            
            log.debug("Cached value for key: {} with TTL: {} minutes", key, ttlMinutes);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get value from cache
     */
    public Object get(String key) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                log.debug("Cache miss for key: {}", key);
                return null;
            }

            if (entry.isExpired()) {
                log.debug("Cache entry expired for key: {}", key);
                // Remove expired entry (upgrade to write lock)
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    cache.remove(key);
                    return null;
                } finally {
                    lock.readLock().lock();
                    lock.writeLock().unlock();
                }
            }

            // Update last accessed time
            entry.setLastAccessed(LocalDateTime.now());
            log.debug("Cache hit for key: {}", key);
            return entry.getValue();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Remove value from cache
     */
    public void remove(String key) {
        lock.writeLock().lock();
        try {
            cache.remove(key);
            log.debug("Removed cache entry for key: {}", key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if key exists and is not expired
     */
    public boolean containsKey(String key) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            return entry != null && !entry.isExpired();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all cache entries
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            log.info("Cleared all cache entries");
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStatistics getStatistics() {
        lock.readLock().lock();
        try {
            long totalEntries = cache.size();
            long expiredEntries = cache.values().stream()
                    .mapToLong(entry -> entry.isExpired() ? 1 : 0)
                    .sum();
            
            return new CacheStatistics(totalEntries, totalEntries - expiredEntries, expiredEntries);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Increment numeric value (for counters)
     */
    public long increment(String key) {
        return increment(key, 1);
    }

    /**
     * Increment numeric value by specified amount
     */
    public long increment(String key, long delta) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            long newValue;
            
            if (entry == null || entry.isExpired()) {
                newValue = delta;
            } else {
                Object currentValue = entry.getValue();
                if (currentValue instanceof Number) {
                    newValue = ((Number) currentValue).longValue() + delta;
                } else {
                    log.warn("Cannot increment non-numeric value for key: {}", key);
                    newValue = delta;
                }
            }
            
            LocalDateTime expiryTime = LocalDateTime.now().plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES);
            cache.put(key, new CacheEntry(newValue, expiryTime, LocalDateTime.now()));
            
            log.debug("Incremented key: {} by {} to value: {}", key, delta, newValue);
            return newValue;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set operations - add to set
     */
    public void addToSet(String key, Object member) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            Set<Object> set;
            
            if (entry == null || entry.isExpired()) {
                set = new HashSet<>();
            } else {
                Object value = entry.getValue();
                if (value instanceof Set) {
                    set = (Set<Object>) value;
                } else {
                    set = new HashSet<>();
                }
            }
            
            set.add(member);
            LocalDateTime expiryTime = LocalDateTime.now().plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES);
            cache.put(key, new CacheEntry(set, expiryTime, LocalDateTime.now()));
            
            log.debug("Added member to set for key: {}", key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set operations - remove from set
     */
    public boolean removeFromSet(String key, Object member) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                return false;
            }
            
            Object value = entry.getValue();
            if (value instanceof Set) {
                Set<Object> set = (Set<Object>) value;
                boolean removed = set.remove(member);
                
                if (removed) {
                    entry.setLastAccessed(LocalDateTime.now());
                    log.debug("Removed member from set for key: {}", key);
                }
                
                return removed;
            }
            
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Set operations - get set members
     */
    public Set<Object> getSetMembers(String key) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                return new HashSet<>();
            }
            
            Object value = entry.getValue();
            if (value instanceof Set) {
                entry.setLastAccessed(LocalDateTime.now());
                return new HashSet<>((Set<Object>) value); // Return copy
            }
            
            return new HashSet<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Hash operations - put field in hash
     */
    public void putHash(String key, String field, Object value) {
        lock.writeLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            Map<String, Object> hash;
            
            if (entry == null || entry.isExpired()) {
                hash = new HashMap<>();
            } else {
                Object entryValue = entry.getValue();
                if (entryValue instanceof Map) {
                    hash = (Map<String, Object>) entryValue;
                } else {
                    hash = new HashMap<>();
                }
            }
            
            hash.put(field, value);
            LocalDateTime expiryTime = LocalDateTime.now().plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES);
            cache.put(key, new CacheEntry(hash, expiryTime, LocalDateTime.now()));
            
            log.debug("Put hash field {} for key: {}", field, key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Hash operations - get field from hash
     */
    public Object getHashField(String key, String field) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                return null;
            }
            
            Object value = entry.getValue();
            if (value instanceof Map) {
                Map<String, Object> hash = (Map<String, Object>) value;
                entry.setLastAccessed(LocalDateTime.now());
                return hash.get(field);
            }
            
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Hash operations - get all fields from hash
     */
    public Map<String, Object> getHash(String key) {
        lock.readLock().lock();
        try {
            CacheEntry entry = cache.get(key);
            if (entry == null || entry.isExpired()) {
                return new HashMap<>();
            }
            
            Object value = entry.getValue();
            if (value instanceof Map) {
                entry.setLastAccessed(LocalDateTime.now());
                return new HashMap<>((Map<String, Object>) value); // Return copy
            }
            
            return new HashMap<>();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Cleanup expired entries
     */
    private void cleanupExpiredEntries() {
        lock.writeLock().lock();
        try {
            int initialSize = cache.size();
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
            int removedCount = initialSize - cache.size();
            
            if (removedCount > 0) {
                log.debug("Cleaned up {} expired cache entries", removedCount);
            }
        } catch (Exception e) {
            log.error("Error during cache cleanup: {}", e.getMessage(), e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Evict least recently used entries when cache is full
     */
    private void evictLeastRecentlyUsed() {
        // Find oldest entry by last accessed time
        String oldestKey = cache.entrySet().stream()
                .min(Map.Entry.<String, CacheEntry>comparingByValue(
                        Comparator.comparing(CacheEntry::getLastAccessed)))
                .map(Map.Entry::getKey)
                .orElse(null);
        
        if (oldestKey != null) {
            cache.remove(oldestKey);
            log.debug("Evicted LRU cache entry: {}", oldestKey);
        }
    }

    /**
     * Shutdown cleanup
     */
    public void shutdown() {
        if (cleanupTimer != null) {
            cleanupTimer.cancel();
        }
        clear();
        log.info("InMemoryCacheService shutdown completed");
    }

    /**
     * Cache entry with expiration and access tracking
     */
    private static class CacheEntry {
        private final Object value;
        private final LocalDateTime expiryTime;
        private volatile LocalDateTime lastAccessed;

        public CacheEntry(Object value, LocalDateTime expiryTime, LocalDateTime lastAccessed) {
            this.value = value;
            this.expiryTime = expiryTime;
            this.lastAccessed = lastAccessed;
        }

        public Object getValue() {
            return value;
        }

        public LocalDateTime getExpiryTime() {
            return expiryTime;
        }

        public LocalDateTime getLastAccessed() {
            return lastAccessed;
        }

        public void setLastAccessed(LocalDateTime lastAccessed) {
            this.lastAccessed = lastAccessed;
        }

        public boolean isExpired() {
            return LocalDateTime.now().isAfter(expiryTime);
        }
    }

    /**
     * Cache statistics
     */
    public static class CacheStatistics {
        private final long totalEntries;
        private final long activeEntries;
        private final long expiredEntries;

        public CacheStatistics(long totalEntries, long activeEntries, long expiredEntries) {
            this.totalEntries = totalEntries;
            this.activeEntries = activeEntries;
            this.expiredEntries = expiredEntries;
        }

        public long getTotalEntries() {
            return totalEntries;
        }

        public long getActiveEntries() {
            return activeEntries;
        }

        public long getExpiredEntries() {
            return expiredEntries;
        }

        @Override
        public String toString() {
            return String.format("CacheStatistics{total=%d, active=%d, expired=%d}", 
                    totalEntries, activeEntries, expiredEntries);
        }
    }
}
