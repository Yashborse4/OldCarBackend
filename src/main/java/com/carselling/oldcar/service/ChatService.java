package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.chat.ChatMessageDto;
import com.carselling.oldcar.dto.chat.ChatRoomDto;
import com.carselling.oldcar.dto.chat.CreateChatRoomRequest;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.model.Car;
import com.carselling.oldcar.model.ChatMessage;
import com.carselling.oldcar.model.ChatRoom;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.CarRepository;
import com.carselling.oldcar.repository.ChatMessageRepository;
import com.carselling.oldcar.repository.ChatRoomRepository;
import com.carselling.oldcar.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chat Service for managing chat functionality
 * Handles chat rooms, messages, WebSocket communication, and real-time messaging
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    /**
     * Create a new chat room between two users about a specific car
     */
    public ChatRoomDto createChatRoom(CreateChatRoomRequest request, Long buyerId) {
        log.info("Creating chat room for car ID: {} between buyer: {} and seller: {}", 
                request.getCarId(), buyerId, request.getSellerId());

        // Validate car exists and is available
        Car car = carRepository.findById(request.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with ID: " + request.getCarId()));

        if (!car.isAvailable()) {
            throw new IllegalStateException("Cannot create chat for unavailable car");
        }

        // Validate users exist
        User buyer = userRepository.findById(buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Buyer not found with ID: " + buyerId));
        
        User seller = userRepository.findById(request.getSellerId())
                .orElseThrow(() -> new ResourceNotFoundException("Seller not found with ID: " + request.getSellerId()));

        // Check if buyer is trying to chat about their own car
        if (buyerId.equals(request.getSellerId())) {
            throw new IllegalArgumentException("Cannot create chat room with yourself");
        }

        // Check if chat room already exists for this car and these users
        ChatRoom existingRoom = chatRoomRepository
                .findByCarIdAndBuyerIdAndSellerId(request.getCarId(), buyerId, request.getSellerId())
                .orElse(null);

        if (existingRoom != null) {
            log.info("Returning existing chat room ID: {}", existingRoom.getId());
            return convertToDto(existingRoom);
        }

        // Create new chat room
        ChatRoom chatRoom = ChatRoom.builder()
                .car(car)
                .buyer(buyer)
                .seller(seller)
                .active(true)
                .createdAt(LocalDateTime.now())
                .lastMessageAt(LocalDateTime.now())
                .build();

        chatRoom = chatRoomRepository.save(chatRoom);

        log.info("Created new chat room ID: {}", chatRoom.getId());
        return convertToDto(chatRoom);
    }

    /**
     * Send a message in a chat room
     */
    public ChatMessageDto sendMessage(Long roomId, String content, Long senderId) {
        log.info("Sending message in room ID: {} from user ID: {}", roomId, senderId);

        // Validate chat room exists and user has access
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + roomId));

        if (!hasAccessToChatRoom(chatRoom, senderId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }

        if (!chatRoom.isActive()) {
            throw new IllegalStateException("Cannot send message to inactive chat room");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found with ID: " + senderId));

        // Create and save message
        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(content)
                .messageType(ChatMessage.MessageType.TEXT)
                .sentAt(LocalDateTime.now())
                .isRead(false)
                .build();

        message = chatMessageRepository.save(message);

        // Update chat room's last message time
        chatRoom.setLastMessageAt(LocalDateTime.now());
        chatRoomRepository.save(chatRoom);

        ChatMessageDto messageDto = convertToDto(message);

        // Send real-time message via WebSocket
        sendRealTimeMessage(chatRoom, messageDto);

        log.info("Message sent successfully with ID: {}", message.getId());
        return messageDto;
    }

    /**
     * Get chat rooms for a user with pagination
     */
    public Page<ChatRoomDto> getUserChatRooms(Long userId, Pageable pageable) {
        log.info("Getting chat rooms for user ID: {}", userId);

        Page<ChatRoom> chatRooms = chatRoomRepository.findByUserIdOrderByLastMessageAtDesc(userId, pageable);
        return chatRooms.map(this::convertToDto);
    }

    /**
     * Get messages in a chat room with pagination
     */
    public Page<ChatMessageDto> getChatRoomMessages(Long roomId, Long userId, Pageable pageable) {
        log.info("Getting messages for room ID: {} for user ID: {}", roomId, userId);

        // Validate access to chat room
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + roomId));

        if (!hasAccessToChatRoom(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }

        Page<ChatMessage> messages = chatMessageRepository.findByChatRoomIdOrderBySentAtDesc(roomId, pageable);
        return messages.map(this::convertToDto);
    }

    /**
     * Mark messages as read
     */
    public void markMessagesAsRead(Long roomId, Long userId) {
        log.info("Marking messages as read for room ID: {} by user ID: {}", roomId, userId);

        // Validate access to chat room
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + roomId));

        if (!hasAccessToChatRoom(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }

        // Mark all unread messages from the other user as read
        List<ChatMessage> unreadMessages = chatMessageRepository
                .findUnreadMessagesForUser(roomId, userId);

        unreadMessages.forEach(message -> message.setRead(true));
        chatMessageRepository.saveAll(unreadMessages);

        log.info("Marked {} messages as read", unreadMessages.size());
    }

    /**
     * Get unread message count for a user
     */
    public long getUnreadMessageCount(Long userId) {
        log.info("Getting unread message count for user ID: {}", userId);
        return chatMessageRepository.countUnreadMessagesForUser(userId);
    }

    /**
     * Close/deactivate a chat room
     */
    public void closeChatRoom(Long roomId, Long userId) {
        log.info("Closing chat room ID: {} by user ID: {}", roomId, userId);

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + roomId));

        if (!hasAccessToChatRoom(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }

        chatRoom.setActive(false);
        chatRoomRepository.save(chatRoom);

        log.info("Chat room closed successfully");
    }

    /**
     * Search chat messages
     */
    public Page<ChatMessageDto> searchMessages(String query, Long userId, Pageable pageable) {
        log.info("Searching messages for query: '{}' by user ID: {}", query, userId);

        Page<ChatMessage> messages = chatMessageRepository.searchMessagesForUser(query, userId, pageable);
        return messages.map(this::convertToDto);
    }

    /**
     * Get chat statistics for a user
     */
    public ChatStatistics getChatStatistics(Long userId) {
        log.info("Getting chat statistics for user ID: {}", userId);

        long totalChatRooms = chatRoomRepository.countByUserId(userId);
        long activeChatRooms = chatRoomRepository.countActiveByUserId(userId);
        long totalMessages = chatMessageRepository.countByUserId(userId);
        long unreadMessages = chatMessageRepository.countUnreadMessagesForUser(userId);

        return ChatStatistics.builder()
                .totalChatRooms(totalChatRooms)
                .activeChatRooms(activeChatRooms)
                .totalMessages(totalMessages)
                .unreadMessages(unreadMessages)
                .build();
    }

    /**
     * Send real-time message via WebSocket
     */
    private void sendRealTimeMessage(ChatRoom chatRoom, ChatMessageDto messageDto) {
        // Send to buyer
        String buyerDestination = "/user/" + chatRoom.getBuyer().getId() + "/queue/messages";
        messagingTemplate.convertAndSend(buyerDestination, messageDto);

        // Send to seller
        String sellerDestination = "/user/" + chatRoom.getSeller().getId() + "/queue/messages";
        messagingTemplate.convertAndSend(sellerDestination, messageDto);

        log.debug("Real-time message sent to both users in room ID: {}", chatRoom.getId());
    }

    /**
     * Check if user has access to chat room
     */
    private boolean hasAccessToChatRoom(ChatRoom chatRoom, Long userId) {
        return chatRoom.getBuyer().getId().equals(userId) || 
               chatRoom.getSeller().getId().equals(userId);
    }

    /**
     * Convert ChatRoom entity to DTO
     */
    private ChatRoomDto convertToDto(ChatRoom chatRoom) {
        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .carId(chatRoom.getCar().getId())
                .carTitle(chatRoom.getCar().getTitle())
                .carImageUrl(chatRoom.getCar().getImageUrls().isEmpty() ? 
                            null : chatRoom.getCar().getImageUrls().get(0))
                .buyerId(chatRoom.getBuyer().getId())
                .buyerName(chatRoom.getBuyer().getFullName())
                .buyerAvatarUrl(chatRoom.getBuyer().getProfileImageUrl())
                .sellerId(chatRoom.getSeller().getId())
                .sellerName(chatRoom.getSeller().getFullName())
                .sellerAvatarUrl(chatRoom.getSeller().getProfileImageUrl())
                .active(chatRoom.isActive())
                .createdAt(chatRoom.getCreatedAt())
                .lastMessageAt(chatRoom.getLastMessageAt())
                .unreadCount(chatMessageRepository.countUnreadMessagesInRoom(chatRoom.getId()))
                .build();
    }

    /**
     * Convert ChatMessage entity to DTO
     */
    private ChatMessageDto convertToDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .roomId(message.getChatRoom().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderAvatarUrl(message.getSender().getProfileImageUrl())
                .content(message.getContent())
                .messageType(message.getMessageType().toString())
                .sentAt(message.getSentAt())
                .isRead(message.isRead())
                .build();
    }

    /**
     * Chat statistics data structure
     */
    @lombok.Builder
    @lombok.Data
    public static class ChatStatistics {
        private long totalChatRooms;
        private long activeChatRooms;
        private long totalMessages;
        private long unreadMessages;
    }
}
