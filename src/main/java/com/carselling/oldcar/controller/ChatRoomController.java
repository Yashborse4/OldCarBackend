package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * Controller for Chat Room Management.
 * Handles creation, retrieval, updates, and participant management of chat
 * rooms.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatService chatService;

    // ========================= CHAT ROOM MANAGEMENT =========================

    /**
     * Create a private chat between two users
     */
    @PostMapping("/private")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createPrivateChat(
            @Valid @RequestBody CreatePrivateChatRequest request,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        ChatRoomDto chatRoom = chatService.createPrivateChat(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Private chat created successfully", chatRoom));
    }

    /**
     * Create a group chat
     */
    @PostMapping("/group")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createGroupChat(
            @Valid @RequestBody CreateGroupChatRequest request,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        ChatRoomDto chatRoom = chatService.createGroupChat(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Group chat created successfully", chatRoom));
    }

    /**
     * Create a car inquiry chat
     */
    @PostMapping("/car-inquiry")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createCarInquiryChat(
            @Valid @RequestBody CreateCarInquiryChatRequest request,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        ChatRoomDto chatRoom = chatService.createCarInquiryChat(request, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Car inquiry chat created successfully", chatRoom));
    }

    // ========================= DEALER INQUIRIES =========================

    /**
     * Get dealer inquiries
     */
    @GetMapping("/dealer/inquiries")
    public ResponseEntity<ApiResponse<Page<ChatRoomDto>>> getDealerInquiries(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Page<ChatRoomDto> inquiries = chatService.getDealerInquiries(currentUser.getId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Dealer inquiries retrieved successfully", inquiries));
    }

    /**
     * Update status of an inquiry
     */
    @PatchMapping("/rooms/{chatRoomId}/inquiry/status")
    public ResponseEntity<ApiResponse<ChatRoomDto>> updateInquiryStatus(
            @PathVariable Long chatRoomId,
            @RequestParam String status,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        ChatRoomDto updatedRoom = chatService.updateInquiryStatus(chatRoomId, status, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Inquiry status updated successfully", updatedRoom));
    }

    /**
     * Get all chat rooms for the current user
     */
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse<Page<ChatRoomDto>>> getUserChatRooms(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Page<ChatRoomDto> chatRooms = chatService.getUserChatRooms(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Chat rooms retrieved successfully", chatRooms));
    }

    /**
     * Get specific chat room details
     */
    @GetMapping("/rooms/{chatRoomId}")
    public ResponseEntity<ApiResponse<ChatRoomDto>> getChatRoom(
            @PathVariable Long chatRoomId,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        ChatRoomDto chatRoom = chatService.getChatRoom(chatRoomId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Chat room details retrieved successfully", chatRoom));
    }

    /**
     * Update chat room (name, description, etc.)
     */
    @PutMapping("/rooms/{chatRoomId}")
    public ResponseEntity<ApiResponse<ChatRoomDto>> updateChatRoom(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody UpdateChatRoomRequest request,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        ChatRoomDto updatedRoom = chatService.updateChatRoom(chatRoomId, request, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Chat room updated successfully", updatedRoom));
    }

    // ========================= PARTICIPANT MANAGEMENT =========================

    /**
     * Add participants to a chat room
     */
    @PostMapping("/rooms/{chatRoomId}/participants")
    public ResponseEntity<ApiResponse<Map<String, String>>> addParticipants(
            @PathVariable Long chatRoomId,
            @Valid @RequestBody AddParticipantsRequest request,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        chatService.addParticipants(chatRoomId, request.getUserIds(), currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Participants added successfully",
                Map.of("message", "Participants added successfully")));
    }

    /**
     * Remove participant from chat room
     */
    @DeleteMapping("/rooms/{chatRoomId}/participants/{userId}")
    public ResponseEntity<ApiResponse<Map<String, String>>> removeParticipant(
            @PathVariable Long chatRoomId,
            @PathVariable Long userId,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        chatService.removeParticipant(chatRoomId, userId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Participant removed successfully",
                Map.of("message", "Participant removed successfully")));
    }

    /**
     * Get all participants in a chat room
     */
    @GetMapping("/rooms/{chatRoomId}/participants")
    public ResponseEntity<ApiResponse<List<ChatParticipantDto>>> getChatRoomParticipants(
            @PathVariable Long chatRoomId,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        List<ChatParticipantDto> participants = chatService.getChatRoomParticipants(chatRoomId,
                currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Participants retrieved successfully", participants));
    }

    /**
     * Leave chat room
     */
    @PostMapping("/rooms/{chatRoomId}/leave")
    public ResponseEntity<ApiResponse<Map<String, String>>> leaveChatRoom(
            @PathVariable Long chatRoomId,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();

        chatService.leaveChatRoom(chatRoomId, currentUser.getId());
        return ResponseEntity.ok(ApiResponse.success("Left chat room successfully",
                Map.of("message", "Left chat room successfully")));
    }

    // ========================= DEALER GROUP MANAGEMENT =========================

    /**
     * Get dealer-only group chats
     */
    @GetMapping("/dealer-groups")
    public ResponseEntity<ApiResponse<Page<ChatRoomDto>>> getDealerGroups(
            @PageableDefault(size = 20) Pageable pageable,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        Page<ChatRoomDto> dealerGroups = chatService.getDealerGroups(currentUser.getId(), pageable);
        return ResponseEntity.ok(ApiResponse.success("Dealer groups retrieved successfully", dealerGroups));
    }

    /**
     * Create dealer-only group
     */
    @PostMapping("/dealer-groups")
    public ResponseEntity<ApiResponse<ChatRoomDto>> createDealerGroup(
            @Valid @RequestBody CreateDealerGroupRequest request,
            Authentication authentication) {
        User currentUser = (User) authentication.getPrincipal();
        ChatRoomDto dealerGroup = chatService.createDealerGroup(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Dealer group created successfully", dealerGroup));
    }
}
