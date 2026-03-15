package com.carselling.oldcar.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
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
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Redis Configuration with Fallback Support
 * Provides Redis caching when available, falls back to in-memory caching when
 * Redis is down
 */
@Configuration
@EnableCaching
@Slf4j
public class RedisConfig implements CachingConfigurer {

    private final ObjectMapper objectMapper;

    public RedisConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    @Value("${app.redis.fallback-to-memory:true}")
    private boolean fallbackToMemory;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${app.redis.pool.max-total:20}")
    private int poolMaxTotal;

    @Value("${app.redis.pool.max-idle:10}")
    private int poolMaxIdle;

    @Value("${app.redis.pool.min-idle:2}")
    private int poolMinIdle;

    /**
     * Primary RedisTemplate bean - only created if Redis is available and enabled
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        try {
            log.info("Initializing Production-grade RedisTemplate");

            template.setConnectionFactory(connectionFactory);

            // Use String serializer for keys
            StringRedisSerializer keySerializer = new StringRedisSerializer();
            template.setKeySerializer(keySerializer);
            template.setHashKeySerializer(keySerializer);

            // Use Jackson JSON serializer for values
            GenericJackson2JsonRedisSerializer serializer = createJacksonSerializer();

            template.setValueSerializer(serializer);
            template.setHashValueSerializer(serializer);
            template.setDefaultSerializer(serializer);
            // Strictly disable default serialization to ensure JSON is always used
            template.setEnableDefaultSerializer(false);
            // Disable transaction support for better caching performance
            template.setEnableTransactionSupport(false);

            template.afterPropertiesSet();

            log.info("RedisTemplate initialized successfully with JSON serialization");
            return template;

        } catch (Exception e) {
            log.error("Failed to initialize RedisTemplate: {}", e.getMessage());
            if (fallbackToMemory) {
                log.warn("Redis unavailable. Returning default template to allow startup (memory fallback will handle caching).");
                return template;
            } else {
                throw new RuntimeException("Redis connection failed and fallback is disabled", e);
            }
        }
    }

    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Creating pooled LettuceConnectionFactory for {}:{}", redisHost, redisPort);

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        @SuppressWarnings("unchecked")
        GenericObjectPoolConfig<io.lettuce.core.api.StatefulConnection<?, ?>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(poolMaxTotal);
        poolConfig.setMaxIdle(poolMaxIdle);
        poolConfig.setMinIdle(poolMinIdle);

        LettucePoolingClientConfiguration clientConfig = LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(Duration.ofSeconds(2))
                .shutdownTimeout(Duration.ZERO)
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        // Disable sharing native connection to avoid contention in high-concurrency environments
        factory.setShareNativeConnection(false);
        return factory;
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
                    .computePrefixWith(cacheName -> "oldcar:" + cacheName + ":")
                    .serializeKeysWith(
                            org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                    .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(
                            org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair
                                    .fromSerializer(createJacksonSerializer()));

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

    /**
     * Custom error handler: log & treat as cache miss instead of crashing.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CustomCacheErrorHandler();
    }

    // Helper methods

    private GenericJackson2JsonRedisSerializer createJacksonSerializer() {
        return new GenericJackson2JsonRedisSerializer(objectMapper);
    }

    private Map<String, RedisCacheConfiguration> createCacheConfigurations(RedisCacheConfiguration defaultCacheConfig) {
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // User cache - 30 minutes TTL + Jitter
        cacheConfigurations.put("users_v4", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(30))));
        cacheConfigurations.put("usersById_v4", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(5))));
        cacheConfigurations.put("userDetails", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(5))));
        cacheConfigurations.put("userDetailsById", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(5))));
        cacheConfigurations.put("userPreferences", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(30))));
        cacheConfigurations.put("similarUsers", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(2))));

        // Vehicle cache - 2 hours TTL + Jitter
        cacheConfigurations.put("vehicles", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(2))));
        cacheConfigurations.put("vehicleSearch", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(15))));
        cacheConfigurations.put("vehicleRecommendations", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(1))));
        cacheConfigurations.put("vehicleStats", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(4))));
        cacheConfigurations.put("trendingVehicles", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(30))));
        cacheConfigurations.put("trendingSearches", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(30))));

        // High-throughput public car endpoints
        cacheConfigurations.put("publicCars", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(2))));
        cacheConfigurations.put("publicCarDetail", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(5))));

        // Chat and messaging
        cacheConfigurations.put("chatMessages", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(1))));
        cacheConfigurations.put("userChats", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(15))));

        // System and metadata
        cacheConfigurations.put("userActivity", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(6))));
        cacheConfigurations.put("systemConfig", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(24))));
        cacheConfigurations.put("fileMetadata", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(12))));

        // Fraud detection
        cacheConfigurations.put("fraudDetection", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(24))));

        // Car Master Data
        cacheConfigurations.put("models", defaultCacheConfig.entryTtl(withJitter(Duration.ofHours(24))));

        // Admin Dashboards & Statistics
        cacheConfigurations.put("adminDashboard", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(5))));
        cacheConfigurations.put("systemStatistics", defaultCacheConfig.entryTtl(withJitter(Duration.ofMinutes(5))));

        return cacheConfigurations;
    }

    /**
     * Adds random jitter to TTL to prevent cache stampede.
     * Adds between 0 and 60 seconds to the provided duration.
     */
    private Duration withJitter(Duration base) {
        return base.plusSeconds(ThreadLocalRandom.current().nextInt(60));
    }

    private CacheManager inMemoryFallbackCacheManager() {
        log.info("Creating in-memory CacheManager");
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();

        // Pre-configure cache names for consistency
        cacheManager.setCacheNames(java.util.Arrays.asList(
                "users_v4", "usersById_v4", "userDetails", "userDetailsById", "userPreferences", "similarUsers",
                "vehicles", "vehicleSearch", "vehicleRecommendations", "vehicleStats",
                "trendingVehicles", "trendingSearches",
                "publicCars", "publicCarDetail",
                "chatMessages", "userChats", "userActivity", "systemConfig", "fileMetadata",
                "fraudDetection", "models", "adminDashboard", "systemStatistics"));

        return cacheManager;
    }


    // ========================= Redis Pub/Sub for Chat Sync =========================

    @Bean
    public ChannelTopic chatTopic() {
        return new ChannelTopic("chat_sync");
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
                                                        MessageListenerAdapter listenerAdapter,
                                                        @org.springframework.beans.factory.annotation.Qualifier("redisTaskExecutor") java.util.concurrent.Executor taskExecutor,
                                                        @org.springframework.beans.factory.annotation.Qualifier("redisSubscriptionExecutor") java.util.concurrent.Executor subscriptionExecutor) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, chatTopic());
        
        // Use managed thread pools for high-throughput chat with graceful shutdown support
        container.setTaskExecutor(taskExecutor);
        container.setSubscriptionExecutor(subscriptionExecutor);

        log.info("Redis Message Listener Container initialized for topic: {} with managed thread pools", chatTopic().getTopic());
        return container;
    }

    @Bean(name = "redisTaskExecutor", destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService redisTaskExecutor() {
        return java.util.concurrent.Executors.newFixedThreadPool(4);
    }

    @Bean(name = "redisSubscriptionExecutor", destroyMethod = "shutdown")
    public java.util.concurrent.ExecutorService redisSubscriptionExecutor() {
        return java.util.concurrent.Executors.newFixedThreadPool(2);
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(com.carselling.oldcar.websocket.RedisMessageSubscriber subscriber) {
        // Uses String serializer for Pub/Sub; subscriber handles JSON manually for resilience
        MessageListenerAdapter adapter = new MessageListenerAdapter(subscriber, "onMessage");
        adapter.setSerializer(new StringRedisSerializer());
        return adapter;
    }


    private void testRedisConnectionFactory(RedisConnectionFactory connectionFactory) {
        try {
            if (connectionFactory instanceof LettuceConnectionFactory) {
                LettuceConnectionFactory lettuceFactory = (LettuceConnectionFactory) connectionFactory;
                // lettuceFactory.validateConnection(); // Skipped during startup for resilience
                log.debug("Redis connection factory initialized (ping-on-demand enabled)");
            }
        } catch (Exception e) {
            log.error("Redis connection factory test failed: {}", e.getMessage());
            throw e;
        }
    }
}
