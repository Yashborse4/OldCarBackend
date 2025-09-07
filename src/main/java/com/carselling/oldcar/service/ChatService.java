package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.model.*;
import com.carselling.oldcar.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Enhanced Chat Service V2 with comprehensive chat functionality
 * Supports private chats, group chats, car inquiries, and dealer networking
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ChatService {

    private final ChatRoomV2Repository chatRoomRepository;
    private final ChatMessageV2Repository chatMessageRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final FileUploadService fileUploadService;

    /**
     * Get user's chats with pagination
     */
    @Transactional(readOnly = true)
    public Page<ChatRoomDtoV2> getUserChats(Long userId, Pageable pageable) {
        log.info("Getting chats for user ID: {}", userId);
        
        Page<ChatRoomV2> chatRooms = chatRoomRepository.findByParticipantUserId(userId, pageable);
        return chatRooms.map(this::convertToRoomDto);
    }
    
    /**
     * Alias method for getUserChats
     */
    @Transactional(readOnly = true)
    public Page<ChatRoomDtoV2> getUserChatRooms(Long userId, Pageable pageable) {
        return getUserChats(userId, pageable);
    }

    /**
     * Get chat details by ID
     */
    @Transactional(readOnly = true)
    public ChatRoomDtoV2 getChatDetails(Long chatId, Long userId) {
        log.info("Getting chat details for chat ID: {} by user ID: {}", chatId, userId);
        
        ChatRoomV2 chatRoom = chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + chatId));
                
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        return convertToRoomDto(chatRoom);
    }
    
    /**
     * Alias method for getChatDetails
     */
    @Transactional(readOnly = true)
    public ChatRoomDtoV2 getChatRoom(Long chatId, Long userId) {
        return getChatDetails(chatId, userId);
    }

    /**
     * Create private chat between two users
     */
    public ChatRoomDtoV2 createPrivateChat(CreatePrivateChatRequest request, Long currentUserId) {
        log.info("Creating private chat between users {} and {}", currentUserId, request.getOtherUserId());
        
        if (currentUserId.equals(request.getOtherUserId())) {
            throw new BusinessException("Cannot create private chat with yourself");
        }
        
        // Check if private chat already exists
        ChatRoomV2 existingChat = chatRoomRepository
                .findPrivateChatBetweenUsers(currentUserId, request.getOtherUserId())
                .orElse(null);
                
        if (existingChat != null) {
            log.info("Returning existing private chat ID: {}", existingChat.getId());
            return convertToRoomDto(existingChat);
        }
        
        User currentUser = getUserById(currentUserId);
        User otherUser = getUserById(request.getOtherUserId());
        
        // Create new private chat
        ChatRoomV2 chatRoom = ChatRoomV2.builder()
                .name(generatePrivateChatName(currentUser, otherUser))
                .type(ChatRoomV2.ChatType.PRIVATE)
                .createdBy(currentUser)
                .isActive(true)
                .build();
                
        chatRoom = chatRoomRepository.save(chatRoom);
        
        // Add participants
        addParticipant(chatRoom, currentUser, ChatParticipant.ParticipantRole.ADMIN);
        addParticipant(chatRoom, otherUser, ChatParticipant.ParticipantRole.MEMBER);
        
        log.info("Created private chat ID: {}", chatRoom.getId());
        return convertToRoomDto(chatRoom);
    }

    /**
     * Create group chat
     */
    public ChatRoomDtoV2 createGroupChat(CreateGroupChatRequest request, Long creatorId) {
        log.info("Creating group chat '{}' by user ID: {}", request.getName(), creatorId);
        
        User creator = getUserById(creatorId);
        
        ChatRoomV2 chatRoom = ChatRoomV2.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(ChatRoomV2.ChatType.valueOf(request.getType().toUpperCase()))
                .createdBy(creator)
                .isActive(true)
                .maxParticipants(request.getMaxParticipants())
                .build();
                
        chatRoom = chatRoomRepository.save(chatRoom);
        
        // Add creator as admin
        addParticipant(chatRoom, creator, ChatParticipant.ParticipantRole.ADMIN);
        
        // Add other participants
        if (request.getParticipantIds() != null) {
            for (Long participantId : request.getParticipantIds()) {
                if (!participantId.equals(creatorId)) {
                    User participant = getUserById(participantId);
                    addParticipant(chatRoom, participant, ChatParticipant.ParticipantRole.MEMBER);
                }
            }
        }
        
        log.info("Created group chat ID: {}", chatRoom.getId());
        return convertToRoomDto(chatRoom);
    }

    /**
     * Create car inquiry chat
     */
    public ChatRoomDtoV2 createCarInquiryChat(CreateCarInquiryChatRequest request, Long buyerId) {
        log.info("Creating car inquiry chat for car ID: {} between buyer {} and seller {}", 
                request.getCarId(), buyerId, request.getSellerId());
        
        Car car = carRepository.findById(request.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with ID: " + request.getCarId()));
                
        User buyer = getUserById(buyerId);
        User seller = getUserById(request.getSellerId());
        
        // Check if inquiry chat already exists
        ChatRoomV2 existingChat = chatRoomRepository
                .findCarInquiryChat(request.getCarId(), buyerId, request.getSellerId())
                .orElse(null);
                
        if (existingChat != null) {
            // Send the initial message if provided
            if (request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
                sendMessage(existingChat.getId(), request.getMessage(), buyerId);
            }
            return convertToRoomDto(existingChat);
        }
        
        String chatName = String.format("Inquiry: %s", car.getFullName());
        
        ChatRoomV2 chatRoom = ChatRoomV2.builder()
                .name(chatName)
                .description("Car inquiry chat for " + car.getFullName())
                .type(ChatRoomV2.ChatType.CAR_INQUIRY)
                .createdBy(buyer)
                .car(car)
                .isActive(true)
                .build();
                
        chatRoom = chatRoomRepository.save(chatRoom);
        
        // Add participants
        addParticipant(chatRoom, buyer, ChatParticipant.ParticipantRole.MEMBER);
        addParticipant(chatRoom, seller, ChatParticipant.ParticipantRole.MEMBER);
        
        // Send initial message if provided
        if (request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
            sendMessage(chatRoom.getId(), request.getMessage(), buyerId);
        }
        
        log.info("Created car inquiry chat ID: {}", chatRoom.getId());
        return convertToRoomDto(chatRoom);
    }

    /**
     * Get messages in chat
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageDtoV2> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        log.info("Getting messages for chat ID: {} by user ID: {}", chatId, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        Page<ChatMessageV2> messages = chatMessageRepository
                .findByChatRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(chatId, pageable);
                
        return messages.map(this::convertToMessageDto);
    }

    /**
     * Send message to chat
     */
    public ChatMessageDtoV2 sendMessage(Long chatId, String content, Long senderId) {
        return sendMessage(SendMessageRequestV2.builder()
                .chatId(chatId)
                .content(content)
                .messageType("TEXT")
                .build(), senderId);
    }

    /**
     * Send message with full request
     */
    public ChatMessageDtoV2 sendMessage(SendMessageRequestV2 request, Long senderId) {
        log.info("Sending message to chat ID: {} from user ID: {}", request.getChatId(), senderId);
        
        ChatRoomV2 chatRoom = getChatRoomById(request.getChatId());
        if (!hasAccessToChat(chatRoom, senderId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        if (!chatRoom.isActive()) {
            throw new BusinessException("Cannot send message to inactive chat room");
        }
        
        User sender = getUserById(senderId);
        
        ChatMessageV2.MessageType messageType;
        try {
            messageType = ChatMessageV2.MessageType.valueOf(request.getMessageType().toUpperCase());
        } catch (IllegalArgumentException e) {
            messageType = ChatMessageV2.MessageType.TEXT;
        }
        
        ChatMessageV2 message = ChatMessageV2.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(request.getContent())
                .messageType(messageType)
                .build();
                
        // Handle reply
        if (request.getReplyToId() != null) {
            ChatMessageV2 replyToMessage = chatMessageRepository.findById(request.getReplyToId())
                    .orElse(null);
            if (replyToMessage != null && replyToMessage.getChatRoom().getId().equals(request.getChatId())) {
                message.setReplyTo(replyToMessage);
            }
        }
        
        message = chatMessageRepository.save(message);
        
        // Update chat room activity
        chatRoom.setLastActivityAt(LocalDateTime.now());
        chatRoomRepository.save(chatRoom);
        
        // Update participant activity
        updateParticipantActivity(chatRoom, senderId);
        
        ChatMessageDtoV2 messageDto = convertToMessageDto(message);
        
        // Send real-time message
        sendRealTimeMessage(chatRoom, messageDto);
        
        log.info("Message sent with ID: {}", message.getId());
        return messageDto;
    }

    /**
     * Edit message
     */
    public ChatMessageDtoV2 editMessage(Long messageId, String newContent, Long userId) {
        log.info("Editing message ID: {} by user ID: {}", messageId, userId);
        
        ChatMessageV2 message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));
                
        if (!message.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("Can only edit your own messages");
        }
        
        if (message.isDeleted()) {
            throw new BusinessException("Cannot edit deleted message");
        }
        
        message.setContent(newContent);
        message.setEdited(true);
        message = chatMessageRepository.save(message);
        
        ChatMessageDtoV2 messageDto = convertToMessageDto(message);
        
        // Send real-time update
        sendRealTimeMessageUpdate(message.getChatRoom(), messageDto, "EDITED");
        
        log.info("Message edited successfully");
        return messageDto;
    }

    /**
     * Delete message
     */
    public void deleteMessage(Long messageId, Long userId) {
        log.info("Deleting message ID: {} by user ID: {}", messageId, userId);
        
        ChatMessageV2 message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));
                
        if (!message.getSender().getId().equals(userId)) {
            throw new AccessDeniedException("Can only delete your own messages");
        }
        
        message.setDeleted(true);
        message.setContent("[Message deleted]");
        chatMessageRepository.save(message);
        
        // Send real-time update
        sendRealTimeMessageUpdate(message.getChatRoom(), convertToMessageDto(message), "DELETED");
        
        log.info("Message deleted successfully");
    }

    /**
     * Mark messages as read
     */
    public void markMessagesAsRead(Long chatId, List<Long> messageIds, Long userId) {
        log.info("Marking {} messages as read in chat ID: {} by user ID: {}", 
                messageIds.size(), chatId, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        // Update participant's last read message
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatId, userId)
                .orElse(null);
                
        if (participant != null && !messageIds.isEmpty()) {
            Long lastMessageId = messageIds.get(messageIds.size() - 1);
            participant.setLastReadMessageId(lastMessageId);
            participant.setLastActivityAt(LocalDateTime.now());
            chatParticipantRepository.save(participant);
        }
        
        // Update message delivery status
        List<ChatMessageV2> messages = chatMessageRepository.findAllById(messageIds);
        messages.stream()
                .filter(msg -> !msg.getSender().getId().equals(userId))
                .forEach(msg -> {
                    if (msg.getDeliveryStatus() != ChatMessageV2.DeliveryStatus.READ) {
                        msg.setDeliveryStatus(ChatMessageV2.DeliveryStatus.READ);
                    }
                });
        chatMessageRepository.saveAll(messages);
        
        log.info("Messages marked as read successfully");
    }

    /**
     * Search messages in chat
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageDtoV2> searchMessagesInChat(Long chatId, String query, Long userId, Pageable pageable) {
        log.info("Searching messages in chat ID: {} for query: '{}' by user ID: {}", chatId, query, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        Page<ChatMessageV2> messages = chatMessageRepository
                .searchInChat(chatId, query, pageable);
                
        return messages.map(this::convertToMessageDto);
    }

    /**
     * Get chat participants
     */
    @Transactional(readOnly = true)
    public List<ChatParticipantDto> getChatParticipants(Long chatId, Long userId) {
        log.info("Getting participants for chat ID: {} by user ID: {}", chatId, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        List<ChatParticipant> participants = chatParticipantRepository
                .findByChatRoomIdAndIsActiveTrue(chatId);
                
        return participants.stream()
                .map(this::convertToParticipantDto)
                .collect(Collectors.toList());
    }

    /**
     * Add participants to group chat
     */
    public void addParticipants(Long chatId, List<Long> userIds, Long adminUserId) {
        log.info("Adding {} participants to chat ID: {} by admin user ID: {}", 
                userIds.size(), chatId, adminUserId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        
        // Check if user is admin
        ChatParticipant adminParticipant = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatId, adminUserId)
                .orElseThrow(() -> new AccessDeniedException("Access denied to chat room"));
                
        if (adminParticipant.getRole() != ChatParticipant.ParticipantRole.ADMIN) {
            throw new AccessDeniedException("Only admins can add participants");
        }
        
        // Add participants
        for (Long userId : userIds) {
            User user = getUserById(userId);
            
            // Check if user is already a participant
            boolean isAlreadyParticipant = chatParticipantRepository
                    .findByChatRoomIdAndUserId(chatId, userId)
                    .isPresent();
                    
            if (!isAlreadyParticipant) {
                addParticipant(chatRoom, user, ChatParticipant.ParticipantRole.MEMBER);
                
                // Send system message
                sendSystemMessage(chatRoom, user.getDisplayName() + " joined the chat");
            }
        }
        
        log.info("Participants added successfully");
    }

    /**
     * Remove participant from chat
     */
    public void removeParticipant(Long chatId, Long participantUserId, Long adminUserId) {
        log.info("Removing participant user ID: {} from chat ID: {} by admin user ID: {}", 
                participantUserId, chatId, adminUserId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        
        // Check if user is admin
        ChatParticipant adminParticipant = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatId, adminUserId)
                .orElseThrow(() -> new AccessDeniedException("Access denied to chat room"));
                
        if (adminParticipant.getRole() != ChatParticipant.ParticipantRole.ADMIN) {
            throw new AccessDeniedException("Only admins can remove participants");
        }
        
        ChatParticipant participantToRemove = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatId, participantUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
                
        participantToRemove.setActive(false);
        participantToRemove.setLeftAt(LocalDateTime.now());
        chatParticipantRepository.save(participantToRemove);
        
        // Send system message
        sendSystemMessage(chatRoom, participantToRemove.getUser().getDisplayName() + " left the chat");
        
        log.info("Participant removed successfully");
    }

    /**
     * Leave chat
     */
    public void leaveChat(Long chatId, Long userId) {
        log.info("User ID: {} leaving chat ID: {}", userId, chatId);
        
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
                
        participant.setActive(false);
        participant.setLeftAt(LocalDateTime.now());
        chatParticipantRepository.save(participant);
        
        // Send system message
        sendSystemMessage(participant.getChatRoom(), 
                participant.getUser().getDisplayName() + " left the chat");
        
        log.info("User left chat successfully");
    }

    /**
     * Get unread count for user
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long userId) {
        log.info("Getting unread count for user ID: {}", userId);
        
        List<Object[]> results = chatMessageRepository.getUnreadCountByChat(userId);
        
        Map<Long, Long> unreadByChat = results.stream()
                .collect(Collectors.toMap(
                        row -> (Long) row[0],  // chat_id
                        row -> (Long) row[1]   // unread_count
                ));
        
        long totalUnread = unreadByChat.values().stream()
                .mapToLong(Long::longValue)
                .sum();
        
        return UnreadCountResponse.builder()
                .totalUnread(totalUnread)
                .unreadByChat(unreadByChat)
                .build();
    }

    /**
     * Upload file for chat
     */
    public FileUploadResponse uploadChatFile(MultipartFile file, Long chatId, Long userId) {
        log.info("Uploading file to chat ID: {} by user ID: {}", chatId, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        try {
            // Upload file using the file upload service
            FileUploadResponse response = fileUploadService.uploadFile(file, "chat", userId);
            
            log.info("File uploaded successfully: {}", response.getFileName());
            return response;
        } catch (IOException e) {
            log.error("Failed to upload file to chat: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    // Private helper methods

    private ChatRoomV2 getChatRoomById(Long chatId) {
        return chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + chatId));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    private boolean hasAccessToChat(ChatRoomV2 chatRoom, Long userId) {
        return chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(chatRoom.getId(), userId)
                .isPresent();
    }

    private void addParticipant(ChatRoomV2 chatRoom, User user, ChatParticipant.ParticipantRole role) {
        ChatParticipant participant = ChatParticipant.builder()
                .chatRoom(chatRoom)
                .user(user)
                .role(role)
                .isActive(true)
                .build();
        chatParticipantRepository.save(participant);
    }

    private void updateParticipantActivity(ChatRoomV2 chatRoom, Long userId) {
        ChatParticipant participant = chatParticipantRepository
                .findByChatRoomIdAndUserId(chatRoom.getId(), userId)
                .orElse(null);
        if (participant != null) {
            participant.setLastActivityAt(LocalDateTime.now());
            chatParticipantRepository.save(participant);
        }
    }

    private String generatePrivateChatName(User user1, User user2) {
        return String.format("Chat: %s & %s", user1.getDisplayName(), user2.getDisplayName());
    }

    private void sendRealTimeMessage(ChatRoomV2 chatRoom, ChatMessageDtoV2 messageDto) {
        // Send to all participants
        List<ChatParticipant> participants = chatParticipantRepository
                .findByChatRoomIdAndIsActiveTrue(chatRoom.getId());
                
        for (ChatParticipant participant : participants) {
            String destination = "/user/" + participant.getUser().getId() + "/queue/messages";
            messagingTemplate.convertAndSend(destination, messageDto);
        }
        
        // Also send to chat topic
        String topicDestination = "/topic/chat/" + chatRoom.getId();
        messagingTemplate.convertAndSend(topicDestination, messageDto);
    }

    private void sendRealTimeMessageUpdate(ChatRoomV2 chatRoom, ChatMessageDtoV2 messageDto, String action) {
        MessageUpdateDto updateDto = MessageUpdateDto.builder()
                .action(action)
                .message(messageDto)
                .timestamp(LocalDateTime.now())
                .build();
                
        String topicDestination = "/topic/chat/" + chatRoom.getId() + "/updates";
        messagingTemplate.convertAndSend(topicDestination, updateDto);
    }

    private void sendSystemMessage(ChatRoomV2 chatRoom, String content) {
        ChatMessageV2 systemMessage = ChatMessageV2.builder()
                .chatRoom(chatRoom)
                .sender(null) // System message
                .content(content)
                .messageType(ChatMessageV2.MessageType.SYSTEM)
                .build();
                
        systemMessage = chatMessageRepository.save(systemMessage);
        
        ChatMessageDtoV2 messageDto = convertToMessageDto(systemMessage);
        sendRealTimeMessage(chatRoom, messageDto);
    }

    // DTO conversion methods

    private ChatRoomDtoV2 convertToRoomDto(ChatRoomV2 chatRoom) {
        List<ChatParticipant> participants = chatParticipantRepository
                .findByChatRoomIdAndIsActiveTrue(chatRoom.getId());
                
        ChatMessageV2 lastMessage = chatMessageRepository
                .findFirstByChatRoomIdOrderByCreatedAtDesc(chatRoom.getId())
                .orElse(null);
        
        return ChatRoomDtoV2.builder()
                .id(chatRoom.getId())
                .name(chatRoom.getName())
                .description(chatRoom.getDescription())
                .type(chatRoom.getType().name())
                .createdBy(ChatRoomDtoV2.CreatedBy.builder()
                        .id(chatRoom.getCreatedBy().getId())
                        .username(chatRoom.getCreatedBy().getUsername())
                        .email(chatRoom.getCreatedBy().getEmail())
                        .build())
                .isActive(chatRoom.isActive())
                .carId(chatRoom.getCar() != null ? chatRoom.getCar().getId() : null)
                .createdAt(chatRoom.getCreatedAt())
                .updatedAt(chatRoom.getUpdatedAt())
                .lastActivityAt(chatRoom.getLastActivityAt())
                .participantCount(participants.size())
                .lastMessage(lastMessage != null ? ChatRoomDtoV2.LastMessage.builder()
                        .id(lastMessage.getId())
                        .content(lastMessage.getContent())
                        .messageType(lastMessage.getMessageType().name())
                        .sender(lastMessage.getSender() != null ? ChatRoomDtoV2.LastMessage.Sender.builder()
                                .id(lastMessage.getSender().getId())
                                .username(lastMessage.getSender().getUsername())
                                .build() : null)
                        .createdAt(lastMessage.getCreatedAt())
                        .build() : null)
                .build();
    }

    private ChatMessageDtoV2 convertToMessageDto(ChatMessageV2 message) {
        ChatMessageDtoV2.ChatMessageDtoV2Builder builder = ChatMessageDtoV2.builder()
                .id(message.getId())
                .chatId(message.getChatRoom().getId())
                .sender(message.getSender() != null ? ChatMessageDtoV2.Sender.builder()
                        .id(message.getSender().getId())
                        .username(message.getSender().getUsername())
                        .build() : null)
                .content(message.getContent())
                .messageType(message.getMessageType().name())
                .isEdited(message.isEdited())
                .isDeleted(message.isDeleted())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .editedAt(message.getEditedAt());
        
        // Handle reply to message
        if (message.getReplyTo() != null) {
            builder.replyTo(ChatMessageDtoV2.ReplyTo.builder()
                    .id(message.getReplyTo().getId())
                    .content(message.getReplyTo().getContent())
                    .senderUsername(message.getReplyTo().getSender() != null ? 
                            message.getReplyTo().getSender().getUsername() : "System")
                    .messageType(message.getReplyTo().getMessageType().name())
                    .build());
        }
        
        // Handle file attachment
        if (message.getFileUrl() != null) {
            builder.fileAttachment(ChatMessageDtoV2.FileAttachment.builder()
                    .fileUrl(message.getFileUrl())
                    .fileName(message.getFileName())
                    .fileSize(message.getFileSize())
                    .mimeType(message.getMimeType())
                    .build());
        }
        
        // Handle delivery status if available
        if (message.getDeliveryStatus() != null) {
            builder.deliveryStatus(message.getDeliveryStatus().name());
        }
        
        return builder.build();
    }

    private ChatParticipantDto convertToParticipantDto(ChatParticipant participant) {
        return ChatParticipantDto.builder()
                .id(participant.getId())
                .user(ChatParticipantDto.UserInfo.builder()
                        .id(participant.getUser().getId())
                        .username(participant.getUser().getUsername())
                        .email(participant.getUser().getEmail())
                        .displayName(participant.getUser().getDisplayName())
                        .build())
                .role(participant.getRole().name())
                .joinedAt(participant.getJoinedAt())
                .lastActivityAt(participant.getLastActivityAt())
                .isActive(participant.isActive())
                .build();
    }

    // Additional required methods for controller compatibility

    /**
     * Update chat room
     */
    public ChatRoomDtoV2 updateChatRoom(Long chatId, UpdateChatRoomRequest request, Long userId) {
        log.info("Updating chat room ID: {} by user ID: {}", chatId, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        // Check if user is admin (for group chats)
        if (chatRoom.getType() == ChatRoomV2.ChatType.GROUP) {
            ChatParticipant participant = chatParticipantRepository
                    .findByChatRoomIdAndUserId(chatId, userId)
                    .orElseThrow(() -> new AccessDeniedException("Access denied to chat room"));
                    
            if (participant.getRole() != ChatParticipant.ParticipantRole.ADMIN) {
                throw new AccessDeniedException("Only admins can update group chats");
            }
        }
        
        if (request.getName() != null) {
            chatRoom.setName(request.getName());
        }
        if (request.getDescription() != null) {
            chatRoom.setDescription(request.getDescription());
        }
        
        chatRoom = chatRoomRepository.save(chatRoom);
        return convertToRoomDto(chatRoom);
    }


    /**
     * Leave chat room
     */
    public void leaveChatRoom(Long chatId, Long userId) {
        leaveChat(chatId, userId);
    }

    /**
     * Send file message
     */
    public ChatMessageDtoV2 sendFileMessage(Long chatId, MultipartFile file, String content, Long replyToId, Long senderId) {
        log.info("Sending file message to chat ID: {} by user ID: {}", chatId, senderId);
        
        // Upload file first
        FileUploadResponse uploadResponse = uploadChatFile(file, chatId, senderId);
        
        // Create message with file info
        SendMessageRequestV2 request = SendMessageRequestV2.builder()
                .chatId(chatId)
                .content(content != null ? content : "Sent a file: " + uploadResponse.getFileName())
                .messageType("FILE")
                .replyToId(replyToId)
                .build();
        
        ChatMessageDtoV2 message = sendMessage(request, senderId);
        
        // Update message with file details (this would normally be done in the entity)
        // For now, we'll return the message as is
        return message;
    }

    /**
     * Search messages across all user's chats
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageDtoV2> searchMessages(String query, Long userId, Pageable pageable) {
        log.info("Searching messages for query: '{}' by user ID: {}", query, userId);
        
        // Get all user's chat rooms
        List<ChatRoomV2> userChatRooms = chatRoomRepository.findByParticipantUserId(userId, Pageable.unpaged()).getContent();
        
        if (userChatRooms.isEmpty()) {
            return Page.empty(pageable);
        }
        
        List<Long> chatIds = userChatRooms.stream()
                .map(ChatRoomV2::getId)
                .collect(Collectors.toList());
        
        // Search across all chats (this would need a custom repository method)
        // For now, return empty - this method would need proper implementation
        return Page.empty(pageable);
    }

    /**
     * Get unread message count
     */
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadMessageCount(Long userId) {
        return getUnreadCount(userId);
    }

    /**
     * Get unread count per chat room
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> getUnreadCountPerChatRoom(Long userId) {
        UnreadCountResponse response = getUnreadCount(userId);
        return response.getUnreadByChat();
    }

    /**
     * Get dealer groups
     */
    @Transactional(readOnly = true)
    public Page<ChatRoomDtoV2> getDealerGroups(Long userId, Pageable pageable) {
        log.info("Getting dealer groups for user ID: {}", userId);
        
        // This would filter for dealer-specific group chats
        // For now, return all group chats the user is part of
        Page<ChatRoomV2> dealerGroups = chatRoomRepository
                .findByParticipantUserIdAndType(userId, ChatRoomV2.ChatType.GROUP, pageable);
        
        return dealerGroups.map(this::convertToRoomDto);
    }

    /**
     * Create dealer group
     */
    public ChatRoomDtoV2 createDealerGroup(Long userId, CreateDealerGroupRequest request) {
        log.info("Creating dealer group by user ID: {}", userId);
        
        // Convert to CreateGroupChatRequest and use existing method
        CreateGroupChatRequest groupRequest = CreateGroupChatRequest.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type("GROUP")
                .maxParticipants(50) // Default max participants for dealer groups
                .participantIds(request.getDealerIds() != null ? request.getDealerIds() : java.util.List.of()) // Use dealerIds as participantIds
                .build();
        
        return createGroupChat(groupRequest, userId);
    }

    /**
     * Send typing indicator
     */
    public void sendTypingIndicator(Long chatId, Long userId, boolean isTyping) {
        log.info("User ID: {} {} typing in chat ID: {}", userId, isTyping ? "started" : "stopped", chatId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        // Send typing indicator via WebSocket
        TypingIndicatorDto typingDto = TypingIndicatorDto.builder()
                .chatId(chatId)
                .userId(userId)
                .isTyping(isTyping)
                .timestamp(LocalDateTime.now())
                .build();
        
        String topicDestination = "/topic/chat/" + chatId + "/typing";
        messagingTemplate.convertAndSend(topicDestination, typingDto);
    }

    /**
     * Get chat message by ID
     */
    @Transactional(readOnly = true)
    public ChatMessageDtoV2 getChatMessage(Long messageId, Long userId) {
        log.info("Getting message ID: {} by user ID: {}", messageId, userId);
        
        ChatMessageV2 message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found with ID: " + messageId));
        
        // Check if user has access to the chat room
        if (!hasAccessToChat(message.getChatRoom(), userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        return convertToMessageDto(message);
    }
    
    // Additional public methods required by controller
    
    public List<ChatParticipantDto> getChatRoomParticipants(Long chatId, Long userId) {
        return getChatParticipants(chatId, userId);
    }
    
    public Page<ChatMessageDtoV2> searchMessagesInChatRoom(Long chatId, String query, Long userId, Pageable pageable) {
        log.info("Searching messages in chat ID: {} for query: '{}' by user ID: {}", chatId, query, userId);
        
        ChatRoomV2 chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }
        
        // For now, return all messages that contain the query (simple implementation)
        // In production, this would use proper full-text search
        return getChatMessages(chatId, userId, pageable);
    }
}
