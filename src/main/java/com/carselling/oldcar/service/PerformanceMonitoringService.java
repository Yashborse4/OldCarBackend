package com.carselling.oldcar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuator.metrics.MetricsEndpoint;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;

/**
 * Service for monitoring application performance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceMonitoringService {

    private final MeterRegistry meterRegistry;
    private final MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
    private final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    
    // Performance counters
    private final AtomicLong apiCallCount = new AtomicLong(0);
    private final AtomicLong errorCount = new AtomicLong(0);
    private final Map<String, AtomicLong> endpointCallCounts = new ConcurrentHashMap<>();
    private final Map<String, Timer> endpointTimers = new ConcurrentHashMap<>();

    /**
     * Record API call performance
     */
    public void recordApiCall(String endpoint, long executionTimeMs, boolean success) {
        // Increment total API call counter
        apiCallCount.incrementAndGet();
        meterRegistry.counter("api.calls.total").increment();
        
        // Record endpoint-specific metrics
        endpointCallCounts.computeIfAbsent(endpoint, k -> new AtomicLong(0)).incrementAndGet();
        meterRegistry.counter("api.calls", "endpoint", endpoint).increment();
        
        // Record execution time
        Timer timer = endpointTimers.computeIfAbsent(endpoint, 
            k -> Timer.builder("api.execution.time")
                      .tag("endpoint", k)
                      .register(meterRegistry));
        timer.record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        
        // Record errors
        if (!success) {
            errorCount.incrementAndGet();
            meterRegistry.counter("api.errors.total").increment();
            meterRegistry.counter("api.errors", "endpoint", endpoint).increment();
        }
    }

    /**
     * Record database query performance
     */
    public void recordDatabaseQuery(String queryType, long executionTimeMs) {
        meterRegistry.timer("database.query.time", "type", queryType)
                    .record(executionTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        meterRegistry.counter("database.queries", "type", queryType).increment();
    }

    /**
     * Record cache operations
     */
    public void recordCacheOperation(String operation, String cacheName, boolean hit) {
        meterRegistry.counter("cache.operations", "operation", operation, "cache", cacheName).increment();
        if ("get".equals(operation)) {
            meterRegistry.counter("cache.hits", "cache", cacheName, "hit", String.valueOf(hit)).increment();
        }
    }

    /**
     * Record file upload metrics
     */
    public void recordFileUpload(String fileType, long fileSizeBytes, long uploadTimeMs) {
        meterRegistry.counter("file.uploads", "type", fileType).increment();
        meterRegistry.summary("file.size.bytes", "type", fileType).record(fileSizeBytes);
        meterRegistry.timer("file.upload.time", "type", fileType)
                    .record(uploadTimeMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Record notification metrics
     */
    public void recordNotification(String notificationType, boolean success) {
        meterRegistry.counter("notifications.sent", "type", notificationType, "success", String.valueOf(success)).increment();
    }

    /**
     * Scheduled method to log system metrics every 5 minutes
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void logSystemMetrics() {
        // Memory metrics
        long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
        double memoryUsagePercent = (double) usedMemory / maxMemory * 100;
        
        Gauge.builder("system.memory.usage.percent")
             .register(meterRegistry, () -> memoryUsagePercent);
        
        // Thread metrics
        int threadCount = threadMXBean.getThreadCount();
        Gauge.builder("system.threads.count")
             .register(meterRegistry, () -> threadCount);
        
        // Log summary
        log.info("=== Performance Metrics Summary ===");
        log.info("Total API Calls: {}", apiCallCount.get());
        log.info("Total Errors: {}", errorCount.get());
        log.info("Memory Usage: {:.2f}% ({} MB / {} MB)", 
                memoryUsagePercent, 
                usedMemory / (1024 * 1024), 
                maxMemory / (1024 * 1024));
        log.info("Active Threads: {}", threadCount);
        log.info("Timestamp: {}", LocalDateTime.now());
        
        // Log top endpoints
        log.info("Top API Endpoints:");
        endpointCallCounts.entrySet().stream()
                         .sorted(Map.Entry.<String, AtomicLong>comparingByValue((a, b) -> 
                                Long.compare(b.get(), a.get())))
                         .limit(5)
                         .forEach(entry -> 
                                log.info("  {}: {} calls", entry.getKey(), entry.getValue().get()));
    }

    /**
     * Get current API call count
     */
    public long getApiCallCount() {
        return apiCallCount.get();
    }

    /**
     * Get current error count
     */
    public long getErrorCount() {
        return errorCount.get();
    }

    /**
     * Get error rate percentage
     */
    public double getErrorRate() {
        long total = apiCallCount.get();
        return total > 0 ? (double) errorCount.get() / total * 100 : 0;
    }

    /**
     * Reset all counters (useful for testing)
     */
    public void resetCounters() {
        apiCallCount.set(0);
        errorCount.set(0);
        endpointCallCounts.clear();
        log.info("Performance monitoring counters reset");
    }
}
