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
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for Chat Message Operations.
 * Handles sending, editing, deleting, and searching messages.
 * Includes Rate Limiting.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Chat Messages", description = "Message operations in chat rooms")
public class ChatMessageController {

        private final ChatService chatService;
        private final com.carselling.oldcar.service.RateLimitingService rateLimitingService;

        // ========================= MESSAGE MANAGEMENT =========================

        /**
         * Send a text message with Rate Limiting
         */
        @PostMapping("/rooms/{chatRoomId}/messages")
        @io.swagger.v3.oas.annotations.Operation(summary = "Send message", description = "Send a message to a chat room")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Message sent successfully"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid message data"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a participant of the chat"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Upload and send file message", description = "Uploads a file and sends it as a message to a chat room")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "File message sent"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "413", description = "File too large"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Rate limit exceeded")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Get messages", description = "Get messages history for a chat room")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Messages retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not a participant")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Edit message", description = "Edit the content of an existing message")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message updated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid content"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the message author"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Message not found")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Delete message", description = "Delete a message from a chat room")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Message deleted"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Not the message author"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Message not found")
        })
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Mark messages as read", description = "Mark one or more messages in a chat room as read")
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
        @io.swagger.v3.oas.annotations.Operation(summary = "Search messages globally", description = "Search messages across all chat rooms the user is part of")
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
