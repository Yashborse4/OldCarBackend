package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.chat.ChatMessageDto;
import com.carselling.oldcar.dto.chat.ChatRoomDto;
import com.carselling.oldcar.dto.chat.CreateChatRoomRequest;
import com.carselling.oldcar.dto.chat.SendMessageRequest;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.service.ChatService;
import com.carselling.oldcar.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * Chat Controller for chat REST API and WebSocket endpoints
 * Handles chat rooms, messaging, and real-time communication
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('VIEWER')")
public class ChatController {

    private final ChatService chatService;

    /**
     * Create a new chat room
     * POST /api/chat/rooms
     */
    @PostMapping("/rooms")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createChatRoom(
            @Valid @RequestBody CreateChatRoomRequest request) {
        
        log.info("Creating chat room for car ID: {}", request.getCarId());
        
        Long buyerId = SecurityUtils.getCurrentUserId();
        ChatRoomDto chatRoom = chatService.createChatRoom(request, buyerId);

        return ResponseEntity.ok(ApiResponse.success(
                "Chat room created successfully",
                "You can now start chatting about this car.",
                chatRoom
        ));
    }

    /**
     * Get user's chat rooms with pagination
     * GET /api/chat/rooms
     */
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<Page<ChatRoomDto>>> getUserChatRooms(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "sort", defaultValue = "lastMessageAt") String sort,
            @RequestParam(value = "direction", defaultValue = "desc") String direction) {
        
        log.info("Getting chat rooms for current user");
        
        Long userId = SecurityUtils.getCurrentUserId();
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) 
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortDirection, sort));

        Page<ChatRoomDto> chatRooms = chatService.getUserChatRooms(userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Chat rooms retrieved successfully",
                String.format("Retrieved %d chat rooms.", chatRooms.getTotalElements()),
                chatRooms
        ));
    }

    /**
     * Get messages in a chat room
     * GET /api/chat/rooms/{roomId}/messages
     */
    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> getChatRoomMessages(
            @PathVariable Long roomId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        
        log.info("Getting messages for chat room ID: {}", roomId);
        
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        Page<ChatMessageDto> messages = chatService.getChatRoomMessages(roomId, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Chat messages retrieved successfully",
                String.format("Retrieved %d messages from this chat.", messages.getTotalElements()),
                messages
        ));
    }

    /**
     * Send a message via REST API (fallback for non-WebSocket clients)
     * POST /api/chat/rooms/{roomId}/messages
     */
    @PostMapping("/rooms/{roomId}/messages")
    public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
            @PathVariable Long roomId,
            @Valid @RequestBody SendMessageRequest request) {
        
        log.info("Sending message to chat room ID: {} via REST", roomId);
        
        Long senderId = SecurityUtils.getCurrentUserId();
        ChatMessageDto message = chatService.sendMessage(roomId, request.getContent(), senderId);

        return ResponseEntity.ok(ApiResponse.success(
                "Message sent successfully",
                "Your message has been delivered.",
                message
        ));
    }

    /**
     * Mark messages as read
     * PUT /api/chat/rooms/{roomId}/read
     */
    @PutMapping("/rooms/{roomId}/read")
    public ResponseEntity<ApiResponse<Object>> markMessagesAsRead(@PathVariable Long roomId) {
        log.info("Marking messages as read for chat room ID: {}", roomId);
        
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.markMessagesAsRead(roomId, userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Messages marked as read",
                "All unread messages in this chat have been marked as read."
        ));
    }

    /**
     * Close a chat room
     * DELETE /api/chat/rooms/{roomId}
     */
    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<ApiResponse<Object>> closeChatRoom(@PathVariable Long roomId) {
        log.info("Closing chat room ID: {}", roomId);
        
        Long userId = SecurityUtils.getCurrentUserId();
        chatService.closeChatRoom(roomId, userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Chat room closed successfully",
                "This chat room has been deactivated."
        ));
    }

    /**
     * Get unread message count
     * GET /api/chat/unread-count
     */
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadMessageCount() {
        log.info("Getting unread message count for current user");
        
        Long userId = SecurityUtils.getCurrentUserId();
        long unreadCount = chatService.getUnreadMessageCount(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Unread count retrieved successfully",
                "Current unread message count.",
                unreadCount
        ));
    }

    /**
     * Search messages
     * GET /api/chat/search
     */
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> searchMessages(
            @RequestParam(value = "query") String query,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        
        log.info("Searching messages with query: '{}'", query);
        
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "sentAt"));

        Page<ChatMessageDto> messages = chatService.searchMessages(query, userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(
                "Message search completed",
                String.format("Found %d messages matching '%s'.", messages.getTotalElements(), query),
                messages
        ));
    }

    /**
     * Get chat statistics for current user
     * GET /api/chat/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<ChatService.ChatStatistics>> getChatStatistics() {
        log.info("Getting chat statistics for current user");
        
        Long userId = SecurityUtils.getCurrentUserId();
        ChatService.ChatStatistics statistics = chatService.getChatStatistics(userId);

        return ResponseEntity.ok(ApiResponse.success(
                "Chat statistics retrieved successfully",
                "Your chat activity statistics.",
                statistics
        ));
    }

    // ============================
    // WebSocket Message Handlers
    // ============================

    /**
     * WebSocket endpoint for sending messages
     * Destination: /app/chat/{roomId}
     * Response: /topic/chat/{roomId}
     */
    @MessageMapping("/chat/{roomId}")
    @SendTo("/topic/chat/{roomId}")
    public ChatMessageDto sendMessageViaWebSocket(
            @DestinationVariable Long roomId,
            @Payload SendMessageRequest request,
            Principal principal) {
        
        log.info("Receiving WebSocket message for room ID: {} from user: {}", roomId, principal.getName());
        
        // Extract user ID from principal (assuming it contains the user ID)
        Long senderId = Long.parseLong(principal.getName());
        
        return chatService.sendMessage(roomId, request.getContent(), senderId);
    }

    /**
     * WebSocket endpoint for joining a chat room
     * Destination: /app/chat/{roomId}/join
     */
    @MessageMapping("/chat/{roomId}/join")
    public void joinChatRoom(
            @DestinationVariable Long roomId,
            Principal principal) {
        
        log.info("User {} joining chat room {}", principal.getName(), roomId);
        
        // Mark messages as read when user joins the room
        Long userId = Long.parseLong(principal.getName());
        chatService.markMessagesAsRead(roomId, userId);
    }

    /**
     * WebSocket endpoint for leaving a chat room
     * Destination: /app/chat/{roomId}/leave
     */
    @MessageMapping("/chat/{roomId}/leave")
    public void leaveChatRoom(
            @DestinationVariable Long roomId,
            Principal principal) {
        
        log.info("User {} leaving chat room {}", principal.getName(), roomId);
        
        // Could implement typing indicators or presence management here
        // For now, just log the event
    }

    /**
     * WebSocket endpoint for typing indicators
     * Destination: /app/chat/{roomId}/typing
     * Response: /topic/chat/{roomId}/typing
     */
    @MessageMapping("/chat/{roomId}/typing")
    @SendTo("/topic/chat/{roomId}/typing")
    public TypingIndicator handleTypingIndicator(
            @DestinationVariable Long roomId,
            @Payload TypingIndicatorRequest request,
            Principal principal) {
        
        log.debug("User {} typing in room {}: {}", principal.getName(), roomId, request.isTyping());
        
        Long userId = Long.parseLong(principal.getName());
        
        return TypingIndicator.builder()
                .userId(userId)
                .roomId(roomId)
                .isTyping(request.isTyping())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ============================
    // WebSocket DTOs
    // ============================

    /**
     * Request object for typing indicator
     */
    @lombok.Data
    public static class TypingIndicatorRequest {
        private boolean typing;
    }

    /**
     * Response object for typing indicator
     */
    @lombok.Builder
    @lombok.Data
    public static class TypingIndicator {
        private Long userId;
        private Long roomId;
        private boolean isTyping;
        private long timestamp;
    }
}
