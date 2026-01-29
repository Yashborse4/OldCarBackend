package com.carselling.oldcar.service.impl;

import com.carselling.oldcar.service.RateLimitingService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimitingServiceImpl implements RateLimitingService {

    // In-memory rate limiting bucket cache using composite key "userId:roomId"
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsumeMessageLimit(Long userId, Long chatRoomId) {
        Bucket bucket = resolveBucket(userId, chatRoomId);
        return bucket.tryConsume(1);
    }

    /**
     * Resolve or create a rate-limit bucket for the user in a specific room.
     * Limit: 20 messages per minute per room.
     */
    private Bucket resolveBucket(Long userId, Long chatRoomId) {
        String key = userId + ":" + chatRoomId;
        return buckets.computeIfAbsent(key, k -> {
            Bandwidth limit = Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        });
    }
}
