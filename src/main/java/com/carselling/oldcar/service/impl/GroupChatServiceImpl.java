package com.carselling.oldcar.service.impl;

import com.carselling.oldcar.b2.B2FileService;
import com.carselling.oldcar.dto.chat.ChatRoomDto;
import com.carselling.oldcar.dto.chat.UpdateGroupDetailsRequest;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.UnauthorizedActionException;
import com.carselling.oldcar.model.ChatParticipant;
import com.carselling.oldcar.model.ChatRoom;
import com.carselling.oldcar.repository.ChatParticipantRepository;
import com.carselling.oldcar.repository.ChatRoomRepository;
import com.carselling.oldcar.service.ChatService;
import com.carselling.oldcar.service.GroupChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.carselling.oldcar.model.ResourceType;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupChatServiceImpl implements GroupChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final UserRepository userRepository;
    private final B2FileService b2FileService;
    private final ChatService chatService;
    private final com.carselling.oldcar.service.ChatAuthorizationService chatAuthorizationService;

    @Override
    @Transactional
    public ChatRoomDto updateGroupDetails(Long chatId, UpdateGroupDetailsRequest request, Long requesterId) {
        ChatRoom chatRoom = getGroupChatAccess(chatId, requesterId);
        chatAuthorizationService.assertIsAdmin(requesterId, chatId);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.length() < 3 || name.length() > 50) {
                throw new IllegalArgumentException("Group name must be between 3 and 50 characters");
            }
            chatRoom.setName(name);
        }
        if (request.getDescription() != null) {
            String description = request.getDescription().trim();
            if (description.length() > 500) {
                throw new IllegalArgumentException("Group description cannot exceed 500 characters");
            }
            chatRoom.setDescription(description);
        }

        ChatRoom savedRoom = chatRoomRepository.save(chatRoom);
        return chatService.getChatRoom(savedRoom.getId(), requesterId);
    }

    @Override
    @Transactional
    public ChatRoomDto updateGroupImage(Long chatId, MultipartFile file, Long requesterId) {
        ChatRoom chatRoom = getGroupChatAccess(chatId, requesterId);
        chatAuthorizationService.assertIsAdmin(requesterId, chatId);
        User user = userRepository.findById(requesterId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Validation handled by FileUploadService

        try {
            // Upload to B2
            String path = String.format("chat/%d", chatId);

            // Use CHAT_ATTACHMENT for now, or we could add GROUP_ICON to ResourceType
            FileUploadResponse uploadResponse = b2FileService.uploadFile(file, path, user, ResourceType.CHAT_ATTACHMENT,
                    chatId);

            chatRoom.setImageUrl(uploadResponse.getFileUrl());
            ChatRoom savedRoom = chatRoomRepository.save(chatRoom);

            return chatService.getChatRoom(savedRoom.getId(), requesterId);
        } catch (Exception e) {
            log.error("Failed to upload group image", e);
            throw new RuntimeException("Failed to upload group image", e);
        }
    }

    private ChatRoom getGroupChatAccess(Long chatId, Long requesterId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found"));

        if (chatRoom.getType() != ChatRoom.ChatType.GROUP && chatRoom.getType() != ChatRoom.ChatType.DEALER_NETWORK) {
            throw new IllegalArgumentException("Not a group chat");
        }

        ChatParticipant participant = chatParticipantRepository.findByChatRoomIdAndUserId(chatId, requesterId)
                .orElseThrow(() -> new UnauthorizedActionException("You are not a participant of this chat"));

        if (!participant.isActive()) {
            throw new UnauthorizedActionException("You are no longer an active participant");
        }

        return chatRoom;
    }
}
