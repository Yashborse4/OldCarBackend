package com.carselling.oldcar.websocket;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.chat.ChatService;
import com.carselling.oldcar.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import org.springframework.security.core.Authentication;
import com.carselling.oldcar.security.UserPrincipal;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket Controller for handling real-time chat messages via STOMP protocol
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    // Deduplication set to prevent duplicate messages
    private static final Set<String> recentlyProcessedMessages = ConcurrentHashMap.newKeySet();
    
    private final ChatService ChatService;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionManager sessionManager;

    // ========================= MESSAGE HANDLING =========================

    /**
     * Handle incoming messages from clients
     */
    @MessageMapping("/ping")
    public void ping() {
        // Heartbeat handler - no action needed, just acknowledgment
    }

    @MessageMapping("/chat/{chatRoomId}/send")
    public void sendMessage(@DestinationVariable Long chatRoomId,
            @Payload SendMessageRequest messageRequest,
            SimpMessageHeaderAccessor headerAccessor,
            Authentication authentication) { // Use Authentication directly if possible, or Principal and cast
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                log.warn("Unauthorized WebSocket message attempt");
                return;
            }

            Long userId = userPrincipal.getId();
            log.debug("User {} sending message to chat room {}", userId, chatRoomId);
            
            // Check for duplicate message using clientMessageId
            String clientMessageId = messageRequest.getClientMessageId();
            if (clientMessageId != null) {
                // Check if we've recently processed this message
                String dedupeKey = userId + ":" + clientMessageId;
                if (recentlyProcessedMessages.contains(dedupeKey)) {
                    log.warn("Duplicate message detected and ignored: {}", clientMessageId);
                    return;
                }
                // Add to processed set and clean old entries
                recentlyProcessedMessages.add(dedupeKey);
                // Keep only last 100 messages per user
                if (recentlyProcessedMessages.size() > 100) {
                    recentlyProcessedMessages.removeIf(key -> key.startsWith(userId + ":"));
                }
            }

            // Send message through service
            ChatMessageDto message = ChatService.sendMessage(messageRequest, userId);

            // Broadcast message to all participants in the chat room
            broadcastMessageToChatRoom(chatRoomId, message);

            // Send real-time notification for unread count updates
            updateUnreadCountsForParticipants(chatRoomId, message.getSender().getId());

        } catch (org.springframework.messaging.MessagingException e) {
            log.error("Messaging error sending WebSocket message: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "Failed to send message: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error sending WebSocket message: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "An unexpected error occurred while sending message.");
            }
        }
    }

    /**
     * Handle message editing
     */
    @MessageMapping("/chat/message/{messageId}/edit")
    public void editMessage(@DestinationVariable Long messageId,
            @Payload EditMessageRequest editRequest,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                log.warn("Unauthorized WebSocket edit attempt");
                return;
            }

            Long userId = userPrincipal.getId();
            ChatMessageDto updatedMessage = ChatService.editMessage(messageId, editRequest.getNewContent(), userId);

            // Create message update DTO
            MessageUpdateDto updateDto = MessageUpdateDto.builder()
                    .action("EDIT")
                    .message(updatedMessage)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            // Broadcast edit to all participants in the chat room
            broadcastMessageUpdateToChatRoom(updatedMessage.getChatRoomId(), updateDto);

        } catch (org.springframework.messaging.MessagingException e) {
            log.error("Messaging error editing WebSocket message: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "Failed to edit message: " + e.getMessage());
            }
        } catch (Exception e) {
            log.error("Unexpected error editing WebSocket message: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "An unexpected error occurred while editing message.");
            }
        }
    }

    /**
     * Handle message deletion
     */
    @MessageMapping("/chat/message/{messageId}/delete")
    public void deleteMessage(@DestinationVariable Long messageId, Authentication authentication) {
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                log.warn("Unauthorized WebSocket delete attempt");
                return;
            }

            Long userId = userPrincipal.getId();

            // Get message details before deletion
            ChatMessageDto messageToDelete = ChatService.getChatMessage(messageId, userId);
            Long chatRoomId = messageToDelete.getChatRoomId();

            // Delete the message
            ChatService.deleteMessage(messageId, userId);

            // Create message update DTO
            MessageUpdateDto updateDto = MessageUpdateDto.builder()
                    .action("DELETE")
                    .message(null) // No message content for delete
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            // Broadcast deletion to all participants in the chat room
            broadcastMessageUpdateToChatRoom(chatRoomId, updateDto);

        } catch (com.carselling.oldcar.exception.ResourceNotFoundException e) {
            log.warn("Message not found during deletion: {}", e.getMessage());
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "Message not found or already deleted.");
            }
        } catch (SecurityException e) {
            log.warn("Unauthorized deletion attempt by user {}: {}",
                    resolveUser(authentication) != null ? resolveUser(authentication).getId() : "unknown",
                    e.getMessage());
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "You are not authorized to delete this message.");
            }
        } catch (Exception e) {
            log.error("Unexpected error deleting WebSocket message: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "Failed to delete message: " + e.getMessage());
            }
        }
    }

    /**
     * Handle typing indicators
     */
    @MessageMapping("/chat/{chatRoomId}/typing")
    public void handleTyping(@DestinationVariable Long chatRoomId,
            @Payload Map<String, Boolean> typingStatus,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                return;
            }

            Long userId = userPrincipal.getId();
            boolean isTyping = typingStatus.getOrDefault("isTyping", false);

            // Update typing status in service
            ChatService.sendTypingIndicator(chatRoomId, userId, isTyping);

            // Get user details
            User user = userService.findById(userId);

            // Create typing indicator DTO
            TypingIndicatorDto typingIndicator = TypingIndicatorDto.builder()
                    .chatId(chatRoomId)
                    .userId(userId)
                    .userName(user.getDisplayName())
                    .isTyping(isTyping)
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            // Broadcast typing indicator to all participants except sender
            messagingTemplate.convertAndSend(
                    "/topic/chat/" + chatRoomId + "/typing",
                    typingIndicator);

        } catch (Exception e) {
            log.error("Error handling typing indicator: {}", e.getMessage(), e);
        }
    }

    /**
     * Handle marking messages as read
     */
    @MessageMapping("/chat/{chatRoomId}/read")
    public void markMessagesAsRead(@DestinationVariable Long chatRoomId,
            @Payload MarkMessagesReadRequest readRequest,
            Authentication authentication) {
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                return;
            }

            Long userId = userPrincipal.getId();
            ChatService.markMessagesAsRead(chatRoomId, readRequest.getMessageIds(), userId);

            // Broadcast read receipts to other participants
            ReadReceiptDto readReceipt = new ReadReceiptDto();
            readReceipt.setChatRoomId(chatRoomId);
            readReceipt.setUserId(userId);
            readReceipt.setMessageIds(readRequest.getMessageIds());

            messagingTemplate.convertAndSend(
                    "/topic/chat/" + chatRoomId + "/read",
                    readReceipt);

            // Update unread counts for the user
            sendUnreadCountUpdate(userId);

        } catch (Exception e) {
            log.error("Error marking messages as read: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "Failed to mark messages as read: " + e.getMessage());
            }
        }
    }

    // ========================= SUBSCRIPTION HANDLING =========================

    /**
     * Handle chat room subscriptions
     */
    @SubscribeMapping("/topic/chat/{chatRoomId}")
    public void subscribeToChat(@DestinationVariable Long chatRoomId, Authentication authentication) {
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                return;
            }

            Long userId = userPrincipal.getId();

            // Verify user has access to this chat room
            ChatService.getChatRoom(chatRoomId, userId);

            // Register user session for this chat room
            sessionManager.addUserToChat(userId, chatRoomId);

            log.debug("User {} subscribed to chat room {}", userId, chatRoomId);

        } catch (Exception e) {
            log.error("Error subscribing to chat room: {}", e.getMessage(), e);
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal != null) {
                sendErrorToUser(userPrincipal.getUsername(), "Failed to subscribe to chat room");
            }
        }
    }

    /**
     * Handle user presence subscriptions
     */
    @SubscribeMapping("/user/queue/presence")
    public void subscribeToPresence(Authentication authentication) {
        try {
            UserPrincipal userPrincipal = resolveUser(authentication);
            if (userPrincipal == null) {
                return;
            }

            Long userId = userPrincipal.getId();
            sessionManager.setUserOnline(userId);

            log.debug("User {} subscribed to presence updates", userId);

        } catch (Exception e) {
            log.error("Error subscribing to presence: {}", e.getMessage(), e);
        }
    }

    private UserPrincipal resolveUser(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal) {
            return (UserPrincipal) authentication.getPrincipal();
        }
        return null;
    }

    // ========================= UTILITY METHODS =========================

    /**
     * Broadcast message to all participants in a chat room
     */
    private void broadcastMessageToChatRoom(Long chatRoomId, ChatMessageDto message) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/messages", message);
        log.debug("Broadcasted message {} to chat room {}", message.getId(), chatRoomId);
    }

    /**
     * Broadcast message updates (edit/delete) to chat room
     */
    private void broadcastMessageUpdateToChatRoom(Long chatRoomId, MessageUpdateDto updateDto) {
        messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/updates", updateDto);
        log.debug("Broadcasted message update to chat room {}", chatRoomId);
    }

    /**
     * Update unread counts for all participants except sender
     */
    private void updateUnreadCountsForParticipants(Long chatRoomId, Long senderId) {
        try {
            // Get all participants
            var participants = ChatService.getChatRoomParticipants(chatRoomId, senderId);

            // Send unread count updates to each participant (except sender)
            participants.stream()
                    .filter(p -> !p.getUserId().equals(senderId))
                    .forEach(participant -> sendUnreadCountUpdate(participant.getUserId()));

        } catch (Exception e) {
            log.error("Error updating unread counts: {}", e.getMessage());
        }
    }

    /**
     * Send unread count update to specific user
     */
    private void sendUnreadCountUpdate(Long userId) {
        try {
            UnreadCountResponse unreadCount = ChatService.getUnreadMessageCount(userId);
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/unread-count",
                    unreadCount);
        } catch (Exception e) {
            log.error("Error sending unread count update to user {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Send error message to specific user
     */
    private void sendErrorToUser(String userId, String errorMessage) {
        messagingTemplate.convertAndSendToUser(
                userId,
                "/queue/errors",
                Map.of("error", errorMessage, "timestamp", System.currentTimeMillis()));
    }
}
