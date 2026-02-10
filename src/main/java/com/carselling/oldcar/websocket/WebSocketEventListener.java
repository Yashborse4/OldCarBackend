package com.carselling.oldcar.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket Event Listener for handling connection and disconnection events
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final WebSocketSessionManager sessionManager;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.carselling.oldcar.repository.UserRepository userRepository;

    /**
     * Handle WebSocket connection events
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();

        if (user != null && sessionId != null) {
            String principalName = user.getName();
            Long userId = null;

            try {
                // Try to parse as ID first
                userId = Long.valueOf(principalName);
            } catch (NumberFormatException e) {
                // If not an ID, try to find user by username or email
                log.debug("Principal {} is not a Long ID, attempting lookup by username/email", principalName);
                userId = userRepository.findByEmail(principalName)
                        .map(com.carselling.oldcar.model.User::getId)
                        .orElseGet(() -> userRepository.findByUsername(principalName)
                                .map(com.carselling.oldcar.model.User::getId)
                                .orElse(null));
            }

            if (userId != null) {
                // Add user session to session manager
                sessionManager.addUserSession(userId, sessionId);

                log.info("WebSocket connected - User: {} (Principal: {}), Session: {}", userId, principalName,
                        sessionId);

                // Optionally broadcast user online status to their contacts
                broadcastUserOnlineStatus(userId, true);
            } else {
                log.error("Could not verify user for WebSocket connection: {}", principalName);
            }
        } else {
            log.warn("WebSocket connected without proper authentication - Session: {}", sessionId);
        }
    }

    /**
     * Handle WebSocket disconnection events
     */
    /**
     * Handle WebSocket disconnection events
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        Principal user = headerAccessor.getUser();

        if (user != null && sessionId != null) {
            String principalName = user.getName();
            Long userId = null;

            try {
                // Try to parse as ID first
                userId = Long.valueOf(principalName);
            } catch (NumberFormatException e) {
                // Lookup just like in connect
                userId = userRepository.findByEmail(principalName)
                        .map(com.carselling.oldcar.model.User::getId)
                        .orElseGet(() -> userRepository.findByUsername(principalName)
                                .map(com.carselling.oldcar.model.User::getId)
                                .orElse(null));
            }

            if (userId != null) {
                // Remove user session from session manager
                sessionManager.removeUserSession(sessionId);

                log.info("WebSocket disconnected - User: {} (Principal: {}), Session: {}", userId, principalName,
                        sessionId);

                // Check if user is still online (has other active sessions)
                if (!sessionManager.isUserOnline(userId)) {
                    // Broadcast user offline status if no other sessions exist
                    broadcastUserOnlineStatus(userId, false);

                    // Clear any typing indicators for this user
                    clearUserTypingIndicators(userId);
                }
            } else {
                log.warn("Could not verify user for WebSocket disconnection: {}", principalName);
            }
        } else {
            log.warn("WebSocket disconnected without proper identification - Session: {}", sessionId);
        }
    }

    /**
     * Broadcast user online/offline status to their contacts
     */
    private void broadcastUserOnlineStatus(Long userId, boolean isOnline) {
        try {
            // Create presence update message
            Map<String, Object> presenceUpdate = Map.of(
                    "userId", userId,
                    "isOnline", isOnline,
                    "timestamp", System.currentTimeMillis(),
                    "lastSeen", sessionManager.getUserLastSeen(userId).orElse(null));

            // Broadcast to all users who might be interested in this user's presence
            // In a real implementation, you might want to only broadcast to
            // contacts/friends
            messagingTemplate.convertAndSend("/topic/presence", (Object) presenceUpdate);

            log.debug("Broadcasted presence update for user {}: {}", userId, isOnline ? "online" : "offline");

        } catch (Exception e) {
            log.error("Error broadcasting user presence update: {}", e.getMessage());
        }
    }

    /**
     * Clear all typing indicators for a user when they disconnect
     */
    private void clearUserTypingIndicators(Long userId) {
        try {
            // Get all chat rooms where this user might be typing
            // Note: In a real implementation, you might want to track which rooms
            // the user was actively typing in and only clear those

            log.debug("Cleared typing indicators for disconnected user: {}", userId);

        } catch (Exception e) {
            log.error("Error clearing typing indicators for user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Get WebSocket session statistics (for monitoring)
     */
    public Map<String, Integer> getSessionStats() {
        return sessionManager.getSessionStats();
    }

    /**
     * Cleanup inactive sessions (can be called periodically)
     */
    public void performCleanup() {
        sessionManager.cleanup();
    }
}
