package com.carselling.oldcar.websocket;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.ChatServiceV2;
import com.carselling.oldcar.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/**
 * WebSocket Controller for handling real-time chat messages via STOMP protocol
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatServiceV2 chatServiceV2;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionManager sessionManager;

    // ========================= MESSAGE HANDLING =========================

    /**
     * Handle incoming messages from clients
     */
    @MessageMapping("/chat/{chatRoomId}/send")
    public void sendMessage(@DestinationVariable Long chatRoomId,
                           @Payload SendMessageRequestV2 messageRequest,
                           SimpMessageHeaderAccessor headerAccessor,
                           Principal principal) {
        try {
            if (principal == null) {
                log.warn("Unauthorized WebSocket message attempt");
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            log.debug("User {} sending message to chat room {}", userId, chatRoomId);

            // Send message through service
            ChatMessageDtoV2 message = chatServiceV2.sendMessage(chatRoomId, messageRequest, userId);

            // Broadcast message to all participants in the chat room
            broadcastMessageToChatRoom(chatRoomId, message);

            // Send real-time notification for unread count updates
            updateUnreadCountsForParticipants(chatRoomId, message.getSender().getId());

        } catch (Exception e) {
            log.error("Error sending WebSocket message: {}", e.getMessage(), e);
            sendErrorToUser(principal.getName(), "Failed to send message: " + e.getMessage());
        }
    }

    /**
     * Handle message editing
     */
    @MessageMapping("/chat/message/{messageId}/edit")
    public void editMessage(@DestinationVariable Long messageId,
                           @Payload EditMessageRequest editRequest,
                           Principal principal) {
        try {
            if (principal == null) {
                log.warn("Unauthorized WebSocket edit attempt");
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            ChatMessageDtoV2 updatedMessage = chatServiceV2.editMessage(messageId, editRequest.getNewContent(), userId);

            // Create message update DTO
            MessageUpdateDto updateDto = new MessageUpdateDto();
            updateDto.setMessageId(messageId);
            updateDto.setType("EDIT");
            updateDto.setUpdatedMessage(updatedMessage);

            // Broadcast edit to all participants in the chat room
            broadcastMessageUpdateToChatRoom(updatedMessage.getChatRoomId(), updateDto);

        } catch (Exception e) {
            log.error("Error editing WebSocket message: {}", e.getMessage(), e);
            sendErrorToUser(principal.getName(), "Failed to edit message: " + e.getMessage());
        }
    }

    /**
     * Handle message deletion
     */
    @MessageMapping("/chat/message/{messageId}/delete")
    public void deleteMessage(@DestinationVariable Long messageId, Principal principal) {
        try {
            if (principal == null) {
                log.warn("Unauthorized WebSocket delete attempt");
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            
            // Get message details before deletion
            ChatMessageDtoV2 messageToDelete = chatServiceV2.getChatMessage(messageId, userId);
            Long chatRoomId = messageToDelete.getChatRoomId();
            
            // Delete the message
            chatServiceV2.deleteMessage(messageId, userId);

            // Create message update DTO
            MessageUpdateDto updateDto = new MessageUpdateDto();
            updateDto.setMessageId(messageId);
            updateDto.setType("DELETE");

            // Broadcast deletion to all participants in the chat room
            broadcastMessageUpdateToChatRoom(chatRoomId, updateDto);

        } catch (Exception e) {
            log.error("Error deleting WebSocket message: {}", e.getMessage(), e);
            sendErrorToUser(principal.getName(), "Failed to delete message: " + e.getMessage());
        }
    }

    /**
     * Handle typing indicators
     */
    @MessageMapping("/chat/{chatRoomId}/typing")
    public void handleTyping(@DestinationVariable Long chatRoomId,
                           @Payload Map<String, Boolean> typingStatus,
                           Principal principal) {
        try {
            if (principal == null) {
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            boolean isTyping = typingStatus.getOrDefault("isTyping", false);

            // Update typing status in service
            chatServiceV2.sendTypingIndicator(chatRoomId, userId, isTyping);

            // Get user details
            User user = userService.findById(userId);
            
            // Create typing indicator DTO
            TypingIndicatorDto typingIndicator = new TypingIndicatorDto();
            typingIndicator.setChatRoomId(chatRoomId);
            typingIndicator.setUserId(userId);
            typingIndicator.setUserName(user.getFullName());
            typingIndicator.setIsTyping(isTyping);

            // Broadcast typing indicator to all participants except sender
            messagingTemplate.convertAndSend(
                "/topic/chat/" + chatRoomId + "/typing", 
                typingIndicator
            );

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
                                  Principal principal) {
        try {
            if (principal == null) {
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            chatServiceV2.markMessagesAsRead(chatRoomId, readRequest.getMessageIds(), userId);

            // Broadcast read receipts to other participants
            ReadReceiptDto readReceipt = new ReadReceiptDto();
            readReceipt.setChatRoomId(chatRoomId);
            readReceipt.setUserId(userId);
            readReceipt.setMessageIds(readRequest.getMessageIds());

            messagingTemplate.convertAndSend(
                "/topic/chat/" + chatRoomId + "/read", 
                readReceipt
            );

            // Update unread counts for the user
            sendUnreadCountUpdate(userId);

        } catch (Exception e) {
            log.error("Error marking messages as read: {}", e.getMessage(), e);
            sendErrorToUser(principal.getName(), "Failed to mark messages as read: " + e.getMessage());
        }
    }

    // ========================= SUBSCRIPTION HANDLING =========================

    /**
     * Handle chat room subscriptions
     */
    @SubscribeMapping("/topic/chat/{chatRoomId}")
    public void subscribeToChat(@DestinationVariable Long chatRoomId, Principal principal) {
        try {
            if (principal == null) {
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            
            // Verify user has access to this chat room
            chatServiceV2.getChatRoom(chatRoomId, userId);
            
            // Register user session for this chat room
            sessionManager.addUserToChat(userId, chatRoomId);
            
            log.debug("User {} subscribed to chat room {}", userId, chatRoomId);

        } catch (Exception e) {
            log.error("Error subscribing to chat room: {}", e.getMessage(), e);
            sendErrorToUser(principal.getName(), "Failed to subscribe to chat room");
        }
    }

    /**
     * Handle user presence subscriptions
     */
    @SubscribeMapping("/user/queue/presence")
    public void subscribeToPresence(Principal principal) {
        try {
            if (principal == null) {
                return;
            }

            Long userId = Long.valueOf(principal.getName());
            sessionManager.setUserOnline(userId);
            
            log.debug("User {} subscribed to presence updates", userId);

        } catch (Exception e) {
            log.error("Error subscribing to presence: {}", e.getMessage(), e);
        }
    }

    // ========================= UTILITY METHODS =========================

    /**
     * Broadcast message to all participants in a chat room
     */
    private void broadcastMessageToChatRoom(Long chatRoomId, ChatMessageDtoV2 message) {
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
            var participants = chatServiceV2.getChatRoomParticipants(chatRoomId, senderId);
            
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
            UnreadCountResponse unreadCount = chatServiceV2.getUnreadMessageCount(userId);
            messagingTemplate.convertAndSendToUser(
                userId.toString(), 
                "/queue/unread-count", 
                unreadCount
            );
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
            Map.of("error", errorMessage, "timestamp", System.currentTimeMillis())
        );
    }
}
