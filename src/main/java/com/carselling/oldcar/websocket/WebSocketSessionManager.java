package com.carselling.oldcar.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Manages WebSocket sessions and user presence for chat functionality
 */
@Component
@Slf4j
public class WebSocketSessionManager {

    // Map of userId to their active session IDs
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    
    // Map of sessionId to userId
    private final Map<String, Long> sessionToUser = new ConcurrentHashMap<>();
    
    // Map of chatRoomId to set of active userIds in that room
    private final Map<Long, Set<Long>> chatRoomUsers = new ConcurrentHashMap<>();
    
    // Map of userId to their last seen timestamp
    private final Map<Long, LocalDateTime> userLastSeen = new ConcurrentHashMap<>();
    
    // Map of userId to their current typing status per chat room
    private final Map<Long, Map<Long, Boolean>> userTypingStatus = new ConcurrentHashMap<>();

    /**
     * Add a new user session
     */
    public void addUserSession(Long userId, String sessionId) {
        userSessions.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>()).add(sessionId);
        sessionToUser.put(sessionId, userId);
        updateUserLastSeen(userId);
        
        log.debug("Added session {} for user {}", sessionId, userId);
    }

    /**
     * Remove user session
     */
    public void removeUserSession(String sessionId) {
        Long userId = sessionToUser.remove(sessionId);
        if (userId != null) {
            Set<String> sessions = userSessions.get(userId);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    userSessions.remove(userId);
                    // Remove user from all chat rooms when last session disconnects
                    removeUserFromAllChatRooms(userId);
                    // Clear typing status
                    userTypingStatus.remove(userId);
                }
            }
            updateUserLastSeen(userId);
            log.debug("Removed session {} for user {}", sessionId, userId);
        }
    }

    /**
     * Add user to a chat room
     */
    public void addUserToChat(Long userId, Long chatRoomId) {
        chatRoomUsers.computeIfAbsent(chatRoomId, k -> new CopyOnWriteArraySet<>()).add(userId);
        updateUserLastSeen(userId);
        
        log.debug("Added user {} to chat room {}", userId, chatRoomId);
    }

    /**
     * Remove user from a chat room
     */
    public void removeUserFromChat(Long userId, Long chatRoomId) {
        Set<Long> users = chatRoomUsers.get(chatRoomId);
        if (users != null) {
            users.remove(userId);
            if (users.isEmpty()) {
                chatRoomUsers.remove(chatRoomId);
            }
        }
        
        // Clear typing status for this chat room
        Map<Long, Boolean> typingInRooms = userTypingStatus.get(userId);
        if (typingInRooms != null) {
            typingInRooms.remove(chatRoomId);
        }
        
        log.debug("Removed user {} from chat room {}", userId, chatRoomId);
    }

    /**
     * Remove user from all chat rooms (when they disconnect)
     */
    private void removeUserFromAllChatRooms(Long userId) {
        chatRoomUsers.values().forEach(users -> users.remove(userId));
        chatRoomUsers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        
        log.debug("Removed user {} from all chat rooms", userId);
    }

    /**
     * Check if user is online (has active sessions)
     */
    public boolean isUserOnline(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * Set user as online (update their last seen)
     */
    public void setUserOnline(Long userId) {
        updateUserLastSeen(userId);
    }

    /**
     * Get all users in a chat room
     */
    public Set<Long> getUsersInChatRoom(Long chatRoomId) {
        return chatRoomUsers.getOrDefault(chatRoomId, new HashSet<>());
    }

    /**
     * Get all online users
     */
    public Set<Long> getOnlineUsers() {
        return new HashSet<>(userSessions.keySet());
    }

    /**
     * Get user's last seen timestamp
     */
    public Optional<LocalDateTime> getUserLastSeen(Long userId) {
        return Optional.ofNullable(userLastSeen.get(userId));
    }

    /**
     * Update user's last seen timestamp
     */
    public void updateUserLastSeen(Long userId) {
        userLastSeen.put(userId, LocalDateTime.now());
    }

    /**
     * Set user typing status in a chat room
     */
    public void setUserTyping(Long userId, Long chatRoomId, boolean isTyping) {
        userTypingStatus
            .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
            .put(chatRoomId, isTyping);
        
        // Auto-clear typing status after 5 seconds if still typing
        if (isTyping) {
            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Map<Long, Boolean> typingInRooms = userTypingStatus.get(userId);
                    if (typingInRooms != null) {
                        Boolean currentStatus = typingInRooms.get(chatRoomId);
                        if (Boolean.TRUE.equals(currentStatus)) {
                            typingInRooms.put(chatRoomId, false);
                        }
                    }
                }
            }, 5000); // 5 seconds
        }
        
        log.debug("Set typing status for user {} in chat room {} to {}", userId, chatRoomId, isTyping);
    }

    /**
     * Get user typing status in a chat room
     */
    public boolean isUserTyping(Long userId, Long chatRoomId) {
        Map<Long, Boolean> typingInRooms = userTypingStatus.get(userId);
        if (typingInRooms != null) {
            return Boolean.TRUE.equals(typingInRooms.get(chatRoomId));
        }
        return false;
    }

    /**
     * Get all users typing in a chat room
     */
    public Set<Long> getUsersTypingInChatRoom(Long chatRoomId) {
        Set<Long> typingUsers = new HashSet<>();
        
        userTypingStatus.forEach((userId, typingInRooms) -> {
            if (Boolean.TRUE.equals(typingInRooms.get(chatRoomId))) {
                typingUsers.add(userId);
            }
        });
        
        return typingUsers;
    }

    /**
     * Get session count for debugging
     */
    public Map<String, Integer> getSessionStats() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalUsers", userSessions.size());
        stats.put("totalSessions", sessionToUser.size());
        stats.put("activeChatRooms", chatRoomUsers.size());
        stats.put("usersWithTypingStatus", userTypingStatus.size());
        
        return stats;
    }

    /**
     * Clear inactive sessions and typing status (cleanup method)
     */
    public void cleanup() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        
        // Remove users who haven't been seen in 30 minutes
        userLastSeen.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoffTime));
        
        // Clear typing status for users who are no longer online
        userTypingStatus.entrySet().removeIf(entry -> !isUserOnline(entry.getKey()));
        
        log.debug("Completed WebSocket session cleanup");
    }
}
