package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.dto.common.ApiResponse;
import com.carselling.oldcar.security.UserPrincipal;
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
import lombok.extern.slf4j.Slf4j;

/**
 * Controller for Chat Room Management.
 * Handles creation, retrieval, updates, and participant management of chat
 * rooms.
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
@io.swagger.v3.oas.annotations.tags.Tag(name = "Chat Rooms", description = "Chat room and participant management")
public class ChatRoomController {

        private final ChatService chatService;
        private final com.carselling.oldcar.service.GroupChatService groupChatService;

        // ========================= CHAT ROOM MANAGEMENT =========================

        /**
         * Create a private chat between two users
         */
        @PostMapping("/private")
        @io.swagger.v3.oas.annotations.Operation(summary = "Create private chat", description = "Create or get a private chat with another user")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Private chat created"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Recipient not found")
        })
        public ResponseEntity<ApiResponse<ChatRoomDto>> createPrivateChatRoom(
                        @RequestParam("recipientId") Long recipientId,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                CreatePrivateChatRequest request = CreatePrivateChatRequest.builder().otherUserId(recipientId).build();
                ChatRoomDto chatRoom = chatService.createPrivateChat(request, currentUser.getId());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Private chat created successfully", chatRoom));
        }

        /**
         * Create a group chat
         */
        @PostMapping("/group")
        @io.swagger.v3.oas.annotations.Operation(summary = "Create group chat", description = "Create a new group chat")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Group chat created"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request data"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
        public ResponseEntity<ApiResponse<ChatRoomDto>> createGroupChatRoom(
                        @Valid @RequestBody CreateGroupChatRequest request,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                ChatRoomDto chatRoom = chatService.createGroupChat(request, currentUser.getId());
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Group chat created successfully", chatRoom));
        }

        /**
         * Create a car inquiry chat
         */
        @PostMapping("/inquiry")
        @io.swagger.v3.oas.annotations.Operation(summary = "Create car inquiry", description = "Start a chat inquiry for a specific car")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Car inquiry created"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Car not found")
        })
        public ResponseEntity<ApiResponse<ChatRoomDto>> createCarInquiryChatRoom(
                        @Valid @RequestBody CreateCarInquiryChatRequest request,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

                ChatRoomDto updatedRoom = chatService.updateInquiryStatus(chatRoomId, status, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Inquiry status updated successfully", updatedRoom));
        }

        /**
         * Get all chat rooms for the current user
         */
        @GetMapping({ "", "/rooms" })
        @io.swagger.v3.oas.annotations.Operation(summary = "Get user chats", description = "Get all chat rooms for the current user")
        @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chat rooms retrieved"),
                        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
        })
        public ResponseEntity<ApiResponse<Page<ChatRoomDto>>> getUserChatRooms(
                        @PageableDefault(size = 20) Pageable pageable,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                Page<ChatRoomDto> chatRooms = chatService.getUserChatRooms(currentUser.getId(), pageable);
                return ResponseEntity.ok(ApiResponse.success("Chat rooms retrieved successfully", chatRooms));
        }

        /**
         * Get specific chat room details
         */
        @GetMapping({ "/{roomId}", "/rooms/{roomId}" })
        @io.swagger.v3.oas.annotations.Operation(summary = "Get chat room", description = "Get details of a specific chat room")
        public ResponseEntity<ApiResponse<ChatRoomDto>> getChatRoom(
                        @PathVariable Long roomId,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

                ChatRoomDto chatRoom = chatService.getChatRoom(roomId, currentUser.getId());
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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();

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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
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
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                ChatRoomDto dealerGroup = chatService.createDealerGroup(currentUser.getId(), request);
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Dealer group created successfully", dealerGroup));
        }

        // ========================= GROUP EDITING =========================

        /**
         * Update group details (Name, Description)
         */
        @PatchMapping("/groups/{chatId}/details")
        public ResponseEntity<ApiResponse<ChatRoomDto>> updateGroupDetails(
                        @PathVariable Long chatId,
                        @Valid @RequestBody UpdateGroupDetailsRequest request,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                ChatRoomDto updatedRoom = groupChatService.updateGroupDetails(chatId, request, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Group details updated successfully", updatedRoom));
        }

        /**
         * Update group profile image
         */
        @PostMapping(value = "/groups/{chatId}/image", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
        public ResponseEntity<ApiResponse<ChatRoomDto>> updateGroupImage(
                        @PathVariable Long chatId,
                        @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                ChatRoomDto updatedRoom = groupChatService.updateGroupImage(chatId, file, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Group image updated successfully", updatedRoom));
        }

        // ========================= GROUP INVITES & SEARCH =========================

        /**
         * Invite user to group
         */
        @PostMapping("/groups/invite")
        public ResponseEntity<ApiResponse<Void>> inviteUserToGroup(
                        @Valid @RequestBody InviteGroupRequest request,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                chatService.inviteUserToGroup(request.getChatId(), request.getUsername(), currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Invitation sent successfully", null));
        }

        /**
         * Accept group invite
         */
        @PostMapping("/groups/invites/{inviteId}/accept")
        public ResponseEntity<ApiResponse<Void>> acceptGroupInvite(
                        @PathVariable Long inviteId,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                chatService.acceptGroupInvite(inviteId, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Invitation accepted", null));
        }

        /**
         * Reject group invite
         */
        @PostMapping("/groups/invites/{inviteId}/reject")
        public ResponseEntity<ApiResponse<Void>> rejectGroupInvite(
                        @PathVariable Long inviteId,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                chatService.rejectGroupInvite(inviteId, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Invitation rejected", null));
        }

        /**
         * Get my pending invites
         */
        @GetMapping("/groups/invites")
        public ResponseEntity<ApiResponse<List<GroupInviteDto>>> getPendingInvites(
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                List<GroupInviteDto> invites = chatService.getPendingInvites(currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Pending invites retrieved", invites));
        }

        /**
         * Search dealers
         */
        @GetMapping("/dealers/search")
        public ResponseEntity<ApiResponse<List<ChatParticipantDto>>> searchDealers(
                        @RequestParam String query,
                        Authentication authentication) {
                List<ChatParticipantDto> dealers = chatService.searchDealers(query);
                return ResponseEntity.ok(ApiResponse.success("Dealers found", dealers));
        }

        /**
         * Search users (broad search)
         */
        @GetMapping("/users/search")
        public ResponseEntity<ApiResponse<List<ChatParticipantDto>>> searchUsers(
                        @RequestParam String query,
                        Authentication authentication) {
                List<ChatParticipantDto> users = chatService.searchUsers(query);
                return ResponseEntity.ok(ApiResponse.success("Users found", users));
        }

        /**
         * Search chat rooms
         */
        @GetMapping("/search")
        @io.swagger.v3.oas.annotations.Operation(summary = "Search chats", description = "Search for chat rooms by name or participant")
        public ResponseEntity<ApiResponse<Page<ChatRoomDto>>> searchChats(
                        @RequestParam String query,
                        @PageableDefault(size = 20) Pageable pageable,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                Page<ChatRoomDto> chatRooms = chatService.searchChats(query, currentUser.getId(), pageable);
                return ResponseEntity.ok(ApiResponse.success("Chats found successfully", chatRooms));
        }

        /**
         * Get chats for a specific car
         */
        @GetMapping("/car/{carId}")
        @io.swagger.v3.oas.annotations.Operation(summary = "Get car chats", description = "Get all chats related to a specific car")
        public ResponseEntity<ApiResponse<java.util.List<ChatRoomDto>>> getCarChats(
                        @PathVariable Long carId,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                java.util.List<ChatRoomDto> chatRooms = chatService.getCarChats(carId, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Car chats retrieved successfully", chatRooms));
        }

        // ========================= INVITE LINKS =========================

        /**
         * Create invite link
         */
        @PostMapping("/rooms/{chatId}/invite-link")
        public ResponseEntity<ApiResponse<Map<String, String>>> createInviteLink(
                        @PathVariable Long chatId,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                String token = chatService.createInviteLink(chatId, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Invite link created", Map.of("token", token)));
        }

        /**
         * Get invite info
         */
        @GetMapping("/invites/{token}")
        public ResponseEntity<ApiResponse<ChatRoomDto>> getInviteInfo(
                        @PathVariable String token,
                        Authentication authentication) {
                ChatRoomDto chatRoom = chatService.getInviteInfo(token);
                return ResponseEntity.ok(ApiResponse.success("Invite info retrieved", chatRoom));
        }

        /**
         * Join chat by invite link
         */
        @PostMapping("/invites/{token}/join")
        public ResponseEntity<ApiResponse<ChatRoomDto>> joinChatByLink(
                        @PathVariable String token,
                        Authentication authentication) {
                UserPrincipal currentUser = (UserPrincipal) authentication.getPrincipal();
                ChatRoomDto chatRoom = chatService.joinChatByLink(token, currentUser.getId());
                return ResponseEntity.ok(ApiResponse.success("Joined chat successfully", chatRoom));
        }
}
