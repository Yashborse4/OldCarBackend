package com.carselling.oldcar.service;

import com.carselling.oldcar.model.DistributedCacheEntry;
import com.carselling.oldcar.repository.DistributedCacheRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class DistributedFallbackCacheService {

    private final DistributedCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    private static final long DEFAULT_TTL_MINUTES = 60;

    /**
     * Scheduled cleanup of expired entries (Runs every 10 minutes)
     */
    @Scheduled(fixedRateString = "${cache.cleanup.interval:600000}")
    @Transactional
    public void cleanupExpiredEntries() {
        int removed = cacheRepository.deleteExpiredEntries(LocalDateTime.now());
        if (removed > 0) {
            log.debug("Cleaned up {} expired distributed cache entries", removed);
        }
    }

    @Transactional
    public void put(String key, Object value) {
        put(key, value, DEFAULT_TTL_MINUTES);
    }

    @Transactional
    public void put(String key, Object value, long ttlMinutes) {
        try {
            String payload = value instanceof String ? (String) value : objectMapper.writeValueAsString(value);
            LocalDateTime expiry = LocalDateTime.now().plus(ttlMinutes, ChronoUnit.MINUTES);
            
            DistributedCacheEntry entry = cacheRepository.findById(key)
                    .orElse(DistributedCacheEntry.builder().key(key).build());
            
            entry.setValuePayload(payload);
            entry.setExpiryTime(expiry);
            entry.setLastAccessed(LocalDateTime.now());
            
            cacheRepository.save(entry);
        } catch (JsonProcessingException e) {
            log.error("Error serializing cache value for key: {}", key, e);
        }
    }

    @Transactional
    public Object get(String key) {
        return cacheRepository.findById(key).map(entry -> {
            if (entry.isExpired()) {
                cacheRepository.delete(entry);
                return null;
            }
            entry.setLastAccessed(LocalDateTime.now());
            cacheRepository.save(entry);
            return entry.getValuePayload(); // Return raw string. For objects, user needs to deserialize.
        }).orElse(null);
    }

    @Transactional
    public void remove(String key) {
        cacheRepository.deleteById(key);
    }
    
    @Transactional
    public void clear() {
        cacheRepository.deleteAll();
    }
}
