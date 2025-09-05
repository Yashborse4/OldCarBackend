package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.ChatServiceV2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Enhanced Chat Controller V2 with comprehensive chat functionality
 */
@RestController
@RequestMapping("/api/v2/chat")
@CrossOrigin(origins = "*")
public class ChatControllerV2 {

    @Autowired
    private ChatServiceV2 chatServiceV2;

    // ========================= CHAT ROOM MANAGEMENT =========================

    /**
     * Create a private chat between two users
     */
    @PostMapping("/private")
    public ResponseEntity<?> createPrivateChat(
            @Valid @RequestBody CreatePrivateChatRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatRoomDtoV2 chatRoom = chatServiceV2.createPrivateChat(currentUser.getId(), request);
            return ResponseEntity.ok(chatRoom);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create private chat", "message", e.getMessage()));
        }
    }

    /**
     * Create a group chat
     */
    @PostMapping("/group")
    public ResponseEntity<?> createGroupChat(
            @Valid @RequestBody CreateGroupChatRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatRoomDtoV2 chatRoom = chatServiceV2.createGroupChat(currentUser.getId(), request);
            return ResponseEntity.ok(chatRoom);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create group chat", "message", e.getMessage()));
        }
    }

    /**
     * Create a car inquiry chat
     */
    @PostMapping("/car-inquiry")
    public ResponseEntity<?> createCarInquiryChat(
            @Valid @RequestBody CreateCarInquiryChatRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatRoomDtoV2 chatRoom = chatServiceV2.createCarInquiryChat(currentUser.getId(), request);
            return ResponseEntity.ok(chatRoom);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to create car inquiry chat", "message", e.getMessage()));
        }
    }

    /**
     * Get all chat rooms for the current user
     */
    @GetMapping("/rooms")
    public ResponseEntity<?> getUserChatRooms(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Page<ChatRoomDtoV2> chatRooms = chatServiceV2.getUserChatRooms(currentUser.getId(), pageable);
            return ResponseEntity.ok(chatRooms);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch chat rooms", "message", e.getMessage()));
        }
    }

    /**
     * Get specific chat room details
     */
    @GetMapping("/rooms/{chatRoomId}")
    public ResponseEntity<?> getChatRoom(
            @PathVariable Long chatRoomId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatRoomDtoV2 chatRoom = chatServiceV2.getChatRoom(chatRoomId, currentUser.getId());
            return ResponseEntity.ok(chatRoom);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied or chat room not found", "message", e.getMessage()));
        }
    }

    /**
     * Update chat room (name, description, etc.)
     */
    @PutMapping("/rooms/{chatRoomId}")
    public ResponseEntity<?> updateChatRoom(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody UpdateChatRoomRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatRoomDtoV2 updatedRoom = chatServiceV2.updateChatRoom(chatRoomId, request, currentUser.getId());
            return ResponseEntity.ok(updatedRoom);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to update chat room", "message", e.getMessage()));
        }
    }

    // ========================= PARTICIPANT MANAGEMENT =========================

    /**
     * Add participants to a chat room
     */
    @PostMapping("/rooms/{chatRoomId}/participants")
    public ResponseEntity<?> addParticipants(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody AddParticipantsRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            chatServiceV2.addParticipants(chatRoomId, request.getUserIds(), currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "Participants added successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to add participants", "message", e.getMessage()));
        }
    }

    /**
     * Remove participant from chat room
     */
    @DeleteMapping("/rooms/{chatRoomId}/participants/{userId}")
    public ResponseEntity<?> removeParticipant(
            @PathVariable Long chatRoomId,
            @PathVariable Long userId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            chatServiceV2.removeParticipant(chatRoomId, userId, currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "Participant removed successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to remove participant", "message", e.getMessage()));
        }
    }

    /**
     * Get all participants in a chat room
     */
    @GetMapping("/rooms/{chatRoomId}/participants")
    public ResponseEntity<?> getChatRoomParticipants(
            @PathVariable Long chatRoomId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            List<ChatParticipantDto> participants = chatServiceV2.getChatRoomParticipants(chatRoomId, currentUser.getId());
            return ResponseEntity.ok(participants);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to fetch participants", "message", e.getMessage()));
        }
    }

    /**
     * Leave chat room
     */
    @PostMapping("/rooms/{chatRoomId}/leave")
    public ResponseEntity<?> leaveChatRoom(
            @PathVariable Long chatRoomId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            chatServiceV2.leaveChatRoom(chatRoomId, currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "Left chat room successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to leave chat room", "message", e.getMessage()));
        }
    }

    // ========================= MESSAGE MANAGEMENT =========================

    /**
     * Send a text message
     */
    @PostMapping("/rooms/{chatRoomId}/messages")
    public ResponseEntity<?> sendMessage(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody SendMessageRequestV2 request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatMessageDtoV2 message = chatServiceV2.sendMessage(chatRoomId, request, currentUser.getId());
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to send message", "message", e.getMessage()));
        }
    }

    /**
     * Upload and send file/image message
     */
    @PostMapping("/rooms/{chatRoomId}/messages/upload")
    public ResponseEntity<?> sendFileMessage(
            @PathVariable Long chatRoomId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "content", required = false) String content,
            @RequestParam(value = "replyToId", required = false) Long replyToId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatMessageDtoV2 message = chatServiceV2.sendFileMessage(chatRoomId, file, content, replyToId, currentUser.getId());
            return ResponseEntity.ok(message);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to send file message", "message", e.getMessage()));
        }
    }

    /**
     * Get messages in a chat room
     */
    @GetMapping("/rooms/{chatRoomId}/messages")
    public ResponseEntity<?> getChatMessages(
            @PathVariable Long chatRoomId,
            @PageableDefault(size = 50) Pageable pageable,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Page<ChatMessageDtoV2> messages = chatServiceV2.getChatMessages(chatRoomId, currentUser.getId(), pageable);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to fetch messages", "message", e.getMessage()));
        }
    }

    /**
     * Edit a message
     */
    @PutMapping("/messages/{messageId}")
    public ResponseEntity<?> editMessage(
            @PathVariable Long messageId,
            @Valid @RequestBody EditMessageRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatMessageDtoV2 updatedMessage = chatServiceV2.editMessage(messageId, request.getNewContent(), currentUser.getId());
            return ResponseEntity.ok(updatedMessage);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to edit message", "message", e.getMessage()));
        }
    }

    /**
     * Delete a message
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(
            @PathVariable Long messageId,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            chatServiceV2.deleteMessage(messageId, currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "Message deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to delete message", "message", e.getMessage()));
        }
    }

    /**
     * Mark messages as read
     */
    @PostMapping("/rooms/{chatRoomId}/messages/read")
    public ResponseEntity<?> markMessagesAsRead(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody MarkMessagesReadRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            chatServiceV2.markMessagesAsRead(chatRoomId, request.getMessageIds(), currentUser.getId());
            return ResponseEntity.ok(Map.of("message", "Messages marked as read"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to mark messages as read", "message", e.getMessage()));
        }
    }

    // ========================= SEARCH AND UTILITIES =========================

    /**
     * Search messages across all user's chat rooms
     */
    @GetMapping("/search/messages")
    public ResponseEntity<?> searchMessages(
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Page<ChatMessageDtoV2> messages = chatServiceV2.searchMessages(query, currentUser.getId(), pageable);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to search messages", "message", e.getMessage()));
        }
    }

    /**
     * Search messages in specific chat room
     */
    @GetMapping("/rooms/{chatRoomId}/search")
    public ResponseEntity<?> searchMessagesInChatRoom(
            @PathVariable Long chatRoomId,
            @RequestParam String query,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Page<ChatMessageDtoV2> messages = chatServiceV2.searchMessagesInChatRoom(chatRoomId, query, currentUser.getId(), pageable);
            return ResponseEntity.ok(messages);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to search messages in chat room", "message", e.getMessage()));
        }
    }

    /**
     * Get unread message count for user
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount(Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            UnreadCountResponse unreadCount = chatServiceV2.getUnreadMessageCount(currentUser.getId());
            return ResponseEntity.ok(unreadCount);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch unread count", "message", e.getMessage()));
        }
    }

    /**
     * Get unread count per chat room
     */
    @GetMapping("/rooms/unread-count")
    public ResponseEntity<?> getUnreadCountPerRoom(Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Map<Long, Long> unreadCounts = chatServiceV2.getUnreadCountPerChatRoom(currentUser.getId());
            return ResponseEntity.ok(unreadCounts);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to fetch unread counts per room", "message", e.getMessage()));
        }
    }

    // ========================= DEALER GROUP MANAGEMENT =========================

    /**
     * Get dealer-only group chats
     */
    @GetMapping("/dealer-groups")
    public ResponseEntity<?> getDealerGroups(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            Page<ChatRoomDtoV2> dealerGroups = chatServiceV2.getDealerGroups(currentUser.getId(), pageable);
            return ResponseEntity.ok(dealerGroups);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied to dealer groups", "message", e.getMessage()));
        }
    }

    /**
     * Create dealer-only group
     */
    @PostMapping("/dealer-groups")
    public ResponseEntity<?> createDealerGroup(
            @Valid @RequestBody CreateDealerGroupRequest request,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            ChatRoomDtoV2 dealerGroup = chatServiceV2.createDealerGroup(currentUser.getId(), request);
            return ResponseEntity.ok(dealerGroup);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Failed to create dealer group", "message", e.getMessage()));
        }
    }

    // ========================= TYPING INDICATORS =========================

    /**
     * Send typing indicator
     */
    @PostMapping("/rooms/{chatRoomId}/typing")
    public ResponseEntity<?> sendTypingIndicator(
            @PathVariable Long chatRoomId,
            @RequestBody Map<String, Boolean> typingStatus,
            Authentication authentication) {
        try {
            User currentUser = (User) authentication.getPrincipal();
            boolean isTyping = typingStatus.getOrDefault("isTyping", false);
            chatServiceV2.sendTypingIndicator(chatRoomId, currentUser.getId(), isTyping);
            return ResponseEntity.ok(Map.of("message", "Typing indicator sent"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Failed to send typing indicator", "message", e.getMessage()));
        }
    }
}
