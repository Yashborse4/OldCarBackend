package com.carselling.oldcar.service;

import com.carselling.oldcar.model.ChatParticipant;
import com.carselling.oldcar.model.ChatRoom;
import com.carselling.oldcar.repository.ChatParticipantRepository;
import com.carselling.oldcar.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;

    public ChatRoom assertCanViewChat(Long userId, Long chatId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new com.carselling.oldcar.exception.ResourceNotFoundException(
                        "Chat room not found with ID: " + chatId));

        assertIsActive(chatRoom);
        assertIsParticipant(userId, chatRoom.getId());

        return chatRoom;
    }

    public ChatRoom assertCanSendMessage(Long userId, Long chatId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new com.carselling.oldcar.exception.ResourceNotFoundException(
                        "Chat room not found with ID: " + chatId));

        assertIsActive(chatRoom);
        assertIsParticipant(userId, chatRoom.getId());

        return chatRoom;
    }

    public void assertIsAdmin(Long userId, Long chatId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatId, userId)
                .orElseThrow(() -> new AccessDeniedException("Access denied to chat room"));

        if (participant.getRole() != ChatParticipant.ParticipantRole.ADMIN) {
            throw new AccessDeniedException("Only admins can perform this action");
        }
    }

    private void assertIsActive(ChatRoom chatRoom) {
        if (!chatRoom.isActive()) {
            throw new com.carselling.oldcar.exception.BusinessException("Chat room is not active");
        }
    }

    private void assertIsParticipant(Long userId, Long chatId) {
        boolean isParticipant = chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(chatId, userId)
                .isPresent();

        if (!isParticipant) {
            throw new AccessDeniedException("Access denied to chat room");
        }
    }
}
