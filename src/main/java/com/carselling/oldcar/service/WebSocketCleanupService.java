package com.carselling.oldcar.service;

import com.carselling.oldcar.websocket.WebSocketEventListener;
import com.carselling.oldcar.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for WebSocket session cleanup and maintenance tasks
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WebSocketCleanupService {

    private final WebSocketSessionManager sessionManager;
    private final WebSocketEventListener eventListener;

    /**
     * Clean up inactive WebSocket sessions every 15 minutes
     */
    @Scheduled(fixedRate = 900000) // 15 minutes in milliseconds
    public void cleanupInactiveSessions() {
        try {
            log.debug("Starting WebSocket session cleanup...");
            
            // Get stats before cleanup
            Map<String, Integer> statsBefore = sessionManager.getSessionStats();
            
            // Perform cleanup
            eventListener.performCleanup();
            
            // Get stats after cleanup
            Map<String, Integer> statsAfter = sessionManager.getSessionStats();
            
            log.info("WebSocket cleanup completed. Users: {} -> {}, Sessions: {} -> {}, Chat rooms: {} -> {}", 
                statsBefore.get("totalUsers"), statsAfter.get("totalUsers"),
                statsBefore.get("totalSessions"), statsAfter.get("totalSessions"),
                statsBefore.get("activeChatRooms"), statsAfter.get("activeChatRooms"));
                
        } catch (Exception e) {
            log.error("Error during WebSocket session cleanup: {}", e.getMessage(), e);
        }
    }

    /**
     * Log WebSocket statistics every hour for monitoring
     */
    @Scheduled(fixedRate = 3600000) // 1 hour in milliseconds
    public void logSessionStatistics() {
        try {
            Map<String, Integer> stats = sessionManager.getSessionStats();
            
            log.info("WebSocket Statistics - Active Users: {}, Total Sessions: {}, Active Chat Rooms: {}, Users with Typing Status: {}",
                stats.get("totalUsers"),
                stats.get("totalSessions"), 
                stats.get("activeChatRooms"),
                stats.get("usersWithTypingStatus"));
                
        } catch (Exception e) {
            log.error("Error logging WebSocket statistics: {}", e.getMessage(), e);
        }
    }

    /**
     * Get current WebSocket session statistics
     */
    public Map<String, Integer> getCurrentStats() {
        return sessionManager.getSessionStats();
    }

    /**
     * Manual cleanup trigger (useful for admin endpoints)
     */
    public void triggerManualCleanup() {
        log.info("Manual WebSocket cleanup triggered");
        eventListener.performCleanup();
    }
}
