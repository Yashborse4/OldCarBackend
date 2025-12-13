package com.carselling.oldcar.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Redis Health Check Service
 * Monitors Redis connectivity and provides health status
 */
@Service
@Slf4j
public class RedisHealthService implements HealthIndicator {

    @Value("${app.redis.enabled:true}")
    private boolean redisEnabled;

    @Value("${app.redis.health-check.enabled:true}")
    private boolean healthCheckEnabled;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired(required = false)
    private RedisConnectionFactory redisConnectionFactory;

    private final AtomicBoolean redisHealthy = new AtomicBoolean(false);
    private final AtomicReference<String> lastError = new AtomicReference<>("Not yet checked");
    private final AtomicReference<LocalDateTime> lastCheckTime = new AtomicReference<>(LocalDateTime.now());

    /**
     * Scheduled health check every 30 seconds
     */
    @Scheduled(fixedDelayString = "${app.redis.health-check.interval:30s}")
    public void performHealthCheck() {
        if (!redisEnabled || !healthCheckEnabled) {
            return;
        }

        try {
            checkRedisHealth();
        } catch (Exception e) {
            log.warn("Error during scheduled Redis health check: {}", e.getMessage());
        }
    }

    /**
     * Check Redis health status
     */
    public boolean isRedisHealthy() {
        if (!redisEnabled) {
            return false; // Redis is disabled
        }

        // Perform immediate check if last check was more than 60 seconds ago
        LocalDateTime lastCheck = lastCheckTime.get();
        if (lastCheck == null || Duration.between(lastCheck, LocalDateTime.now()).toSeconds() > 60) {
            checkRedisHealth();
        }

        return redisHealthy.get();
    }

    /**
     * Get detailed Redis status
     */
    public RedisStatus getDetailedStatus() {
        RedisStatus status = new RedisStatus();
        status.setEnabled(redisEnabled);
        status.setHealthy(redisHealthy.get());
        status.setLastCheckTime(lastCheckTime.get());
        status.setLastError(lastError.get());
        status.setConnectionFactoryAvailable(redisConnectionFactory != null);
        status.setRedisTemplateAvailable(redisTemplate != null);
        return status;
    }

    /**
     * Spring Boot Actuator Health Check
     */
    @Override
    public Health health() {
        if (!redisEnabled) {
            return Health.up()
                    .withDetail("status", "disabled")
                    .withDetail("message", "Redis is disabled in configuration")
                    .build();
        }

        RedisStatus status = getDetailedStatus();
        
        if (status.isHealthy()) {
            return Health.up()
                    .withDetail("status", "connected")
                    .withDetail("lastCheckTime", status.getLastCheckTime())
                    .withDetail("connectionFactory", status.isConnectionFactoryAvailable())
                    .withDetail("redisTemplate", status.isRedisTemplateAvailable())
                    .build();
        } else {
            return Health.down()
                    .withDetail("status", "disconnected")
                    .withDetail("lastError", status.getLastError())
                    .withDetail("lastCheckTime", status.getLastCheckTime())
                    .withDetail("connectionFactory", status.isConnectionFactoryAvailable())
                    .withDetail("redisTemplate", status.isRedisTemplateAvailable())
                    .withDetail("fallbackActive", true)
                    .build();
        }
    }

    /**
     * Force a Redis health check
     */
    public void forceHealthCheck() {
        checkRedisHealth();
    }

    // Private methods

    private void checkRedisHealth() {
        lastCheckTime.set(LocalDateTime.now());

        try {
            if (redisTemplate == null) {
                setUnhealthy("RedisTemplate not available - likely using fallback mode");
                return;
            }

            // Test basic connectivity
            String testKey = "redis:health:check:" + System.currentTimeMillis();
            String testValue = "OK";

            // Set a test value with short TTL
            redisTemplate.opsForValue().set(testKey, testValue, Duration.ofSeconds(10));

            // Retrieve the test value
            Object retrievedValue = redisTemplate.opsForValue().get(testKey);

            if (!testValue.equals(retrievedValue)) {
                setUnhealthy("Redis health check failed - value mismatch. Expected: " + testValue + ", Got: " + retrievedValue);
                return;
            }

            // Clean up test key
            redisTemplate.delete(testKey);

            // Additional connection factory check
            if (redisConnectionFactory != null) {
                try {
                    redisConnectionFactory.getConnection().ping();
                } catch (Exception e) {
                    log.warn("Redis connection factory ping failed: {}", e.getMessage());
                    // Don't fail health check for ping failure, as basic operations worked
                }
            }

            setHealthy();

        } catch (Exception e) {
            setUnhealthy("Redis health check failed: " + e.getMessage());
            log.debug("Redis health check exception details", e);
        }
    }

    private void setHealthy() {
        boolean wasUnhealthy = !redisHealthy.getAndSet(true);
        if (wasUnhealthy) {
            log.info("Redis connection restored - health check passed");
        } else {
            log.debug("Redis health check passed");
        }
        lastError.set("Healthy");
    }

    private void setUnhealthy(String errorMessage) {
        boolean wasHealthy = redisHealthy.getAndSet(false);
        if (wasHealthy) {
            log.warn("Redis connection lost: {}", errorMessage);
        } else {
            log.debug("Redis still unhealthy: {}", errorMessage);
        }
        lastError.set(errorMessage);
    }

    /**
     * Redis status information
     */
    public static class RedisStatus {
        private boolean enabled;
        private boolean healthy;
        private LocalDateTime lastCheckTime;
        private String lastError;
        private boolean connectionFactoryAvailable;
        private boolean redisTemplateAvailable;

        // Getters and setters
        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isHealthy() {
            return healthy;
        }

        public void setHealthy(boolean healthy) {
            this.healthy = healthy;
        }

        public LocalDateTime getLastCheckTime() {
            return lastCheckTime;
        }

        public void setLastCheckTime(LocalDateTime lastCheckTime) {
            this.lastCheckTime = lastCheckTime;
        }

        public String getLastError() {
            return lastError;
        }

        public void setLastError(String lastError) {
            this.lastError = lastError;
        }

        public boolean isConnectionFactoryAvailable() {
            return connectionFactoryAvailable;
        }

        public void setConnectionFactoryAvailable(boolean connectionFactoryAvailable) {
            this.connectionFactoryAvailable = connectionFactoryAvailable;
        }

        public boolean isRedisTemplateAvailable() {
            return redisTemplateAvailable;
        }

        public void setRedisTemplateAvailable(boolean redisTemplateAvailable) {
            this.redisTemplateAvailable = redisTemplateAvailable;
        }

        @Override
        public String toString() {
            return String.format("RedisStatus{enabled=%s, healthy=%s, lastCheck=%s, error='%s'}", 
                    enabled, healthy, lastCheckTime, lastError);
        }
    }
}
