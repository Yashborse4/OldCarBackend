package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.dto.common.ApiResponse;

import com.carselling.oldcar.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

/**
 * Controller for Chat Message Operations.
 * Handles sending, editing, deleting, and searching messages.
 * Includes Rate Limiting.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatMessageController {

        private final ChatService chatService;
        private final com.carselling.oldcar.service.RateLimitingService rateLimitingService;

        // ========================= MESSAGE MANAGEMENT =========================

        /**
         * Send a text message with Rate Limiting
         */
        @PostMapping("/rooms/{chatRoomId}/messages")
        public ResponseEntity<ApiResponse<ChatMessageDto>> sendMessage(
                        @PathVariable Long chatRoomId,
                        @Valid @RequestBody SendMessageRequest request,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {

                // Rate Limiting Check
                if (!rateLimitingService.tryConsumeMessageLimit(currentUser.getId(), chatRoomId)) {
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .body(ApiResponse.error("Rate limit exceeded for this room. Please wait.",
                                                        "RATE_LIMIT_EXCEEDED"));
                }

                request.setChatId(chatRoomId); // Set the chat room ID from path
                ChatMessageDto message = chatService.sendMessage(request, currentUser.getId());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Message sent successfully", message));
        }

        /**
         * Upload and send file/image message
         */
        @PostMapping("/rooms/{chatRoomId}/messages/upload")
        public ResponseEntity<ApiResponse<ChatMessageDto>> sendFileMessage(
                        @PathVariable Long chatRoomId,
                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                        @RequestParam(value = "content", required = false) String content,
                        @RequestParam(value = "replyToId", required = false) Long replyToId,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {

                // Rate Limiting Check (Per Room)
                if (!rateLimitingService.tryConsumeMessageLimit(currentUser.getId(), chatRoomId)) {
                        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                                        .body(ApiResponse.error("Rate limit exceeded for this room. Please wait.",
                                                        "RATE_LIMIT_EXCEEDED"));
                }

                ChatMessageDto message = chatService.sendFileMessage(chatRoomId, file, content, replyToId,
                                currentUser.getId());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("File sent successfully", message));
        }

        /**
         * Get messages in a chat room
         */
        @GetMapping("/rooms/{chatRoomId}/messages")
        public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> getChatMessages(
                        @PathVariable Long chatRoomId,
                        @PageableDefault(size = 50) Pageable pageable,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                Page<ChatMessageDto> messages = chatService.getChatMessages(chatRoomId, currentUser.getId(), pageable);
                return ResponseEntity.ok(ApiResponse.success("Messages retrieved successfully", messages));
        }

        /**
         * Edit a message
         */
        @PutMapping("/messages/{messageId}")
        public ResponseEntity<ApiResponse<ChatMessageDto>> editMessage(
                        @PathVariable Long messageId,
                        @Valid @RequestBody EditMessageRequest request,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                ChatMessageDto updatedMessage = chatService.editMessage(messageId, request.getNewContent(),
                                currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Message updated successfully", updatedMessage));
        }

        /**
         * Delete a message
         */
        @DeleteMapping("/messages/{messageId}")
        public ResponseEntity<ApiResponse<Map<String, String>>> deleteMessage(
                        @PathVariable Long messageId,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                chatService.deleteMessage(messageId, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Message deleted successfully",
                                Map.of("message", "Message deleted successfully")));
        }

        /**
         * Mark messages as read
         */
        @PostMapping("/rooms/{chatRoomId}/messages/read")
        public ResponseEntity<ApiResponse<Map<String, String>>> markMessagesAsRead(
                        @PathVariable Long chatRoomId,
                        @Valid @RequestBody MarkMessagesReadRequest request,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                chatService.markMessagesAsRead(chatRoomId, request.getMessageIds(), currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Messages marked as read",
                                Map.of("message", "Messages marked as read")));
        }

        // ========================= SEARCH AND UTILITIES =========================

        /**
         * Search messages across all user's chat rooms
         */
        @GetMapping("/search/messages")
        public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> searchMessages(
                        @RequestParam String query,
                        @PageableDefault(size = 20) Pageable pageable,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                Page<ChatMessageDto> messages = chatService.searchMessages(query, currentUser.getId(), pageable);
                return ResponseEntity.ok(ApiResponse.success("Messages found successfully", messages));
        }

        /**
         * Search messages in specific chat room
         */
        @GetMapping("/rooms/{chatRoomId}/search")
        public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> searchMessagesInChatRoom(
                        @PathVariable Long chatRoomId,
                        @RequestParam String query,
                        @PageableDefault(size = 20) Pageable pageable,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                Page<ChatMessageDto> messages = chatService.searchMessagesInChatRoom(chatRoomId, query,
                                currentUser.getId(), pageable);
                return ResponseEntity.ok(ApiResponse.success("Messages found in chat room", messages));
        }

        /**
         * Get unread message count for user
         */
        @GetMapping("/unread-count")
        public ResponseEntity<ApiResponse<UnreadCountResponse>> getUnreadCount(
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                UnreadCountResponse unreadCount = chatService.getUnreadMessageCount(currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Unread count retrieved successfully", unreadCount));
        }

        /**
         * Get unread count per chat room
         */
        @GetMapping("/rooms/unread-count")
        public ResponseEntity<ApiResponse<Map<Long, Long>>> getUnreadCountPerRoom(
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                Map<Long, Long> unreadCounts = chatService.getUnreadCountPerChatRoom(currentUser.getId());
                return ResponseEntity
                                .ok(ApiResponse.success("Unread count per room retrieved successfully", unreadCounts));
        }

        // ========================= TYPING INDICATORS =========================

        /**
         * Send typing indicator
         */
        @PostMapping("/rooms/{chatRoomId}/typing")
        public ResponseEntity<ApiResponse<Map<String, String>>> sendTypingIndicator(
                        @PathVariable Long chatRoomId,
                        @RequestBody Map<String, Boolean> typingStatus,
                        @org.springframework.security.core.annotation.AuthenticationPrincipal com.carselling.oldcar.security.UserPrincipal currentUser) {
                boolean isTyping = typingStatus.getOrDefault("isTyping", false);
                chatService.sendTypingIndicator(chatRoomId, currentUser.getId(), isTyping);
                return ResponseEntity.ok(ApiResponse.success("Typing indicator sent",
                                Map.of("message", "Typing indicator sent")));
        }
}
