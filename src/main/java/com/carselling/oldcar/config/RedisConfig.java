package com.carselling.oldcar.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis Configuration for Caching
 */
@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use Jackson JSON serializer for values
        Jackson2JsonRedisSerializer<Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(new JavaTimeModule());
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);

        template.setValueSerializer(jackson2JsonRedisSerializer);
        template.setHashValueSerializer(jackson2JsonRedisSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(1)) // Default 1 hour TTL
                .disableCachingNullValues();

        // Specific cache configurations
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // User cache - 30 minutes TTL
        cacheConfigurations.put("users", 
            defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        
        // Vehicle cache - 2 hours TTL
        cacheConfigurations.put("vehicles", 
            defaultCacheConfig.entryTtl(Duration.ofHours(2)));
        
        // Vehicle search results - 15 minutes TTL (shorter for dynamic data)
        cacheConfigurations.put("vehicleSearch", 
            defaultCacheConfig.entryTtl(Duration.ofMinutes(15)));
        
        // Vehicle recommendations - 1 hour TTL
        cacheConfigurations.put("vehicleRecommendations", 
            defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        
        // Vehicle statistics - 4 hours TTL
        cacheConfigurations.put("vehicleStats", 
            defaultCacheConfig.entryTtl(Duration.ofHours(4)));
        
        // Chat messages - 1 hour TTL
        cacheConfigurations.put("chatMessages", 
            defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        
        // User activity logs - 6 hours TTL
        cacheConfigurations.put("userActivity", 
            defaultCacheConfig.entryTtl(Duration.ofHours(6)));
        
        // System configurations - 24 hours TTL
        cacheConfigurations.put("systemConfig", 
            defaultCacheConfig.entryTtl(Duration.ofHours(24)));
        
        // File metadata - 12 hours TTL
        cacheConfigurations.put("fileMetadata", 
            defaultCacheConfig.entryTtl(Duration.ofHours(12)));
        
        // Trending vehicles - 30 minutes TTL
        cacheConfigurations.put("trendingVehicles", 
            defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
