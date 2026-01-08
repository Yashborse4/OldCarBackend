package com.carselling.oldcar.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration with Fallback Support
 * Provides Redis caching when available, falls back to in-memory caching when
 * Redis is down
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig {

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    @Value("${app.redis.fallback-to-memory:true}")
    private boolean fallbackToMemory;

    /**
     * Primary RedisTemplate bean - only created if Redis is available and enabled
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        try {
            log.info("Initializing RedisTemplate with connection factory");

            RedisTemplate<String, Object> template = new RedisTemplate<>();
            template.setConnectionFactory(connectionFactory);

            // Use String serializer for keys
            template.setKeySerializer(new StringRedisSerializer());
            template.setHashKeySerializer(new StringRedisSerializer());

            // Use Jackson JSON serializer for values
            ObjectMapper objectMapper = createObjectMapper();
            Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(
                    objectMapper, Object.class);

            template.setValueSerializer(jackson2JsonRedisSerializer);
            template.setHashValueSerializer(jackson2JsonRedisSerializer);

            template.afterPropertiesSet();

            // Test Redis connection
            testRedisConnection(template);

            log.info("RedisTemplate initialized successfully");
            return template;

        } catch (Exception e) {
            log.error("Failed to initialize RedisTemplate: {}", e.getMessage(), e);
            if (fallbackToMemory) {
                log.warn("Redis unavailable, application will use in-memory caching fallback");
                return null; // Allow fallback to work
            } else {
                throw new RuntimeException("Redis connection failed and fallback is disabled", e);
            }
        }
    }

    /**
     * Redis-based CacheManager - used when Redis is available
     */
    @Bean("redisCacheManager")
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        try {
            log.info("Initializing Redis-based CacheManager");

            // Test connection first
            testRedisConnectionFactory(connectionFactory);

            RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofHours(1)) // Default 1 hour TTL
                    .disableCachingNullValues()
                    .serializeKeysWith(
                            org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                    .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(
                            org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                    .fromSerializer(
                                            new Jackson2JsonRedisSerializer<>(createObjectMapper(), Object.class)));

            Map<String, RedisCacheConfiguration> cacheConfigurations = createCacheConfigurations(defaultCacheConfig);

            CacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                    .cacheDefaults(defaultCacheConfig)
                    .withInitialCacheConfigurations(cacheConfigurations)
                    .build();

            log.info("Redis-based CacheManager initialized successfully");
            return cacheManager;

        } catch (Exception e) {
            log.error("Failed to initialize Redis CacheManager: {}", e.getMessage(), e);
            if (fallbackToMemory) {
                log.warn("Falling back to in-memory CacheManager");
                return inMemoryFallbackCacheManager();
            } else {
                throw new RuntimeException("Redis CacheManager initialization failed and fallback is disabled", e);
            }
        }
    }

    @Bean
    @Primary
    public CacheManager primaryCacheManager(RedisConnectionFactory connectionFactory) {
        if (redisEnabled) {
            try {
                return redisCacheManager(connectionFactory);
            } catch (Exception e) {
                log.warn("Redis CacheManager failed, using in-memory fallback: {}", e.getMessage());
                return inMemoryFallbackCacheManager();
            }
        } else {
            log.info("Redis disabled, using in-memory CacheManager");
            return inMemoryFallbackCacheManager();
        }
    }

    /**
     * Redis health check service
     */
    @Bean
    public RedisHealthService redisHealthService() {
        return new RedisHealthService();
    }

    // Helper methods

    private ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    private Map<String, RedisCacheConfiguration> createCacheConfigurations(RedisCacheConfiguration defaultCacheConfig) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User cache - 30 minutes TTL
        cacheConfigurations.put("users", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("userPreferences", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("similarUsers", defaultCacheConfig.entryTtl(Duration.ofHours(2)));

        // Vehicle cache - 2 hours TTL
        cacheConfigurations.put("vehicles", defaultCacheConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("vehicleSearch", defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        cacheConfigurations.put("vehicleRecommendations", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("vehicleStats", defaultCacheConfig.entryTtl(Duration.ofHours(4)));
        cacheConfigurations.put("trendingVehicles", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("trendingSearches", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));

        // Chat and messaging
        cacheConfigurations.put("chatMessages", defaultCacheConfig.entryTtl(Duration.ofHours(1)));

        // System and metadata
        cacheConfigurations.put("userActivity", defaultCacheConfig.entryTtl(Duration.ofHours(6)));
        cacheConfigurations.put("systemConfig", defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        cacheConfigurations.put("fileMetadata", defaultCacheConfig.entryTtl(Duration.ofHours(12)));

        // Fraud detection
        cacheConfigurations.put("fraudDetection", defaultCacheConfig.entryTtl(Duration.ofHours(24)));

        return cacheConfigurations;
    }

    private CacheManager inMemoryFallbackCacheManager() {
        log.info("Creating in-memory CacheManager");
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();

        // Pre-configure cache names for consistency
        cacheManager.setCacheNames(java.util.Arrays.asList(
                "users", "userPreferences", "similarUsers",
                "vehicles", "vehicleSearch", "vehicleRecommendations", "vehicleStats",
                "trendingVehicles", "trendingSearches",
                "chatMessages", "userActivity", "systemConfig", "fileMetadata",
                "fraudDetection"));

        return cacheManager;
    }

    private void testRedisConnection(RedisTemplate<String, Object> template) {
        try {
            template.opsForValue().set("redis:health:test", "OK", Duration.ofSeconds(5));
            String result = (String) template.opsForValue().get("redis:health:test");
            if (!"OK".equals(result)) {
                throw new RuntimeException("Redis health check failed - value mismatch");
            }
            template.delete("redis:health:test");
            log.debug("Redis connection test successful");
        } catch (Exception e) {
            log.error("Redis connection test failed: {}", e.getMessage());
            throw e;
        }
    }

    private void testRedisConnectionFactory(RedisConnectionFactory connectionFactory) {
        try {
            if (connectionFactory instanceof LettuceConnectionFactory) {
                LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) connectionFactory;
                lettuceFactory.validateConnection();
                log.debug("Redis connection factory test successful");
            }
        } catch (Exception e) {
            log.error("Redis connection factory test failed: {}", e.getMessage());
            throw e;
        }
    }
}
