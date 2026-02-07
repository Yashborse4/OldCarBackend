package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.dto.file.FileUploadResponse;
import com.carselling.oldcar.exception.ResourceNotFoundException;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.model.*;
import com.carselling.oldcar.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
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

    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final ChatGroupInviteRepository chatGroupInviteRepository;
    private final UserRepository userRepository;
    private final CarRepository carRepository;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final FileUploadService fileUploadService;
    private final MediaFinalizationService mediaFinalizationService;
    private final ChatAuthorizationService chatAuthorizationService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Value("${app.chat.max-participants:50}")
    private int maxGroupParticipants;

    /**
     * Get user's chats with pagination
     * Optimized to fetch participants and car details in a single query using
     * EntityGraph
     */
    /**
     * Get user's chats with pagination
     * Optimized to fetch participants and car details in a single query using
     * EntityGraph
     * Also optimized to batch fetch latest messages to avoid N+1
     */
    @Transactional(readOnly = true)
    public Page<ChatRoomDto> getUserChats(Long userId, Pageable pageable) {
        log.info("Getting chats for user ID: {}", userId);

        // 1. Fetch Chat Rooms (Joined with Participants & Car via EntityGraph)
        Page<ChatRoom> chatRoomsPage = chatRoomRepository.findChatRoomsWithParticipants(userId, pageable);

        if (chatRoomsPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // 2. Extract Room IDs
        List<Long> chatRoomIds = chatRoomsPage.getContent().stream()
                .map(ChatRoom::getId)
                .collect(Collectors.toList());

        // 3. Batch Fetch Latest Messages
        List<ChatMessage> latestMessages = chatMessageRepository.findLatestMessagesByChatRoomIds(chatRoomIds);
        Map<Long, ChatMessage> latestMessageMap = latestMessages.stream()
                .collect(Collectors.toMap(
                        msg -> msg.getChatRoom().getId(),
                        msg -> msg,
                        (existing, replacement) -> existing)); // Handle potential duplicates safely

        // 4. Map to DTOs using the pre-fetched map
        return chatRoomsPage.map(chatRoom -> convertToRoomDto(chatRoom, latestMessageMap.get(chatRoom.getId())));
    }

    /**
     * Internal conversion with pre-fetched last message
     */
    private ChatRoomDto convertToRoomDto(ChatRoom chatRoom, ChatMessage lastMessage) {
        // Optimization: Filter participants in memory to avoid N+1 DB calls
        // findChatRoomsWithParticipants already eagerly fetches participants via
        // EntityGraph
        // For getChatDetails, accessing getParticipants() triggers one Lazy load which
        // is acceptable
        List<ChatParticipant> participants = chatRoom.getParticipants() != null
                ? chatRoom.getParticipants().stream()
                        .filter(ChatParticipant::isActive)
                        .collect(Collectors.toList())
                : List.of();

        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .name(chatRoom.getName())
                .description(chatRoom.getDescription())
                .type(chatRoom.getType().name())
                .createdBy(ChatRoomDto.CreatedBy.builder()
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
                .lastMessage(lastMessage != null ? ChatRoomDto.LastMessage.builder()
                        .id(lastMessage.getId())
                        .content(lastMessage.getContent())
                        .messageType(lastMessage.getMessageType().name())
                        .sender(lastMessage.getSender() != null ? ChatRoomDto.LastMessage.Sender.builder()
                                .id(lastMessage.getSender().getId())
                                .username(lastMessage.getSender().getUsername())
                                .build() : null)
                        .createdAt(lastMessage.getCreatedAt())
                        .build() : null)
                .status(chatRoom.getStatus() != null ? chatRoom.getStatus().name() : null)
                .leadScore(chatRoom.getLeadScore())
                .buyerName(chatRoom.getBuyerName())
                .buyerPhone(chatRoom.getBuyerPhone())
                .carInfo(chatRoom.getCar() != null ? ChatRoomDto.CarInfo.builder()
                        .id(chatRoom.getCar().getId())
                        .title(chatRoom.getCar().getFullName())
                        .price(chatRoom.getCar().getPrice() != null ? chatRoom.getCar().getPrice().doubleValue() : 0.0)
                        .imageUrl(chatRoom.getCar().getImages() != null && !chatRoom.getCar().getImages().isEmpty()
                                ? chatRoom.getCar().getImages().get(0)
                                : null)
                        .build() : null)
                .build();
    }

    /**
     * Alias method for getUserChats
     */
    @Transactional(readOnly = true)
    public Page<ChatRoomDto> getUserChatRooms(Long userId, Pageable pageable) {
        return getUserChats(userId, pageable);
    }

    /**
     * Get chat details by ID
     */
    @Transactional(readOnly = true)
    public ChatRoomDto getChatDetails(Long chatId, Long userId) {
        log.info("Getting chat details for chat ID: {} by user ID: {}", chatId, userId);

        ChatRoom chatRoom = chatRoomRepository.findById(chatId)
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
    public ChatRoomDto getChatRoom(Long chatId, Long userId) {
        return getChatDetails(chatId, userId);
    }

    /**
     * Create private chat between two users
     */
    public ChatRoomDto createPrivateChat(CreatePrivateChatRequest request, Long currentUserId) {
        log.info("Creating private chat between users {} and {}", currentUserId, request.getOtherUserId());

        if (currentUserId.equals(request.getOtherUserId())) {
            throw new BusinessException("Cannot create private chat with yourself");
        }

        // Check if private chat already exists
        ChatRoom existingChat = chatRoomRepository
                .findPrivateChatBetweenUsers(currentUserId, request.getOtherUserId())
                .orElse(null);

        if (existingChat != null) {
            log.info("Returning existing private chat ID: {}", existingChat.getId());
            return convertToRoomDto(existingChat);
        }

        User currentUser = getUserById(currentUserId);
        User otherUser = getUserById(request.getOtherUserId());

        // Create new private chat
        ChatRoom chatRoom = ChatRoom.builder()
                .name(generatePrivateChatName(currentUser, otherUser))
                .type(ChatRoom.ChatType.PRIVATE)
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
    public ChatRoomDto createGroupChat(CreateGroupChatRequest request, Long creatorId) {
        log.info("Creating group chat '{}' by user ID: {}", request.getName(), creatorId);

        User creator = getUserById(creatorId);

        ChatRoom chatRoom = ChatRoom.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type(ChatRoom.ChatType.valueOf(request.getType().toUpperCase()))
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
    public ChatRoomDto createCarInquiryChat(CreateCarInquiryChatRequest request, Long buyerId) {
        log.info("Creating car inquiry chat for car ID: {} by buyer {}", request.getCarId(), buyerId);

        Car car = carRepository.findById(request.getCarId())
                .orElseThrow(() -> new ResourceNotFoundException("Car not found with ID: " + request.getCarId()));

        User buyer = getUserById(buyerId);
        User seller;

        if (request.getRecipientId() != null) {
            seller = getUserById(request.getRecipientId());
            // Verify recipient is Owner OR Co-Owner
            boolean isOwner = car.getOwner().getId().equals(seller.getId());
            boolean isCoOwner = car.getCoOwner() != null && car.getCoOwner().getId().equals(seller.getId());

            if (!isOwner && !isCoOwner) {
                throw new BusinessException("The recipient is not associated with this car");
            }
        } else {
            seller = car.getOwner();
        }

        // Validation: Buyer cannot be the Seller (Owner)
        if (buyer.getId().equals(seller.getId())) {
            throw new BusinessException("You cannot inquire about your own car");
        }

        // Validation: Car should be available? (Optional, maybe sold cars can still
        // have chats history, but new inquiries?)
        // For now let's warn but allow, or maybe restrict if sold.
        // Let's restrict if strictly not active/available for new inquiries.
        if (!Boolean.TRUE.equals(car.getIsAvailable()) && !Boolean.TRUE.equals(car.getIsActive())) {
            // throw new BusinessException("This car is no longer available for inquiry");
            // Commented out to allow chatting on closed deals if needed, but standard flow
            // suggests restriction.
        }

        log.info("Buyer: {}, Seller: {}", buyerId, seller.getId());

        // Check if inquiry chat already exists
        ChatRoom existingChat = chatRoomRepository
                .findCarInquiryChat(request.getCarId(), buyerId, seller.getId())
                .orElse(null);

        if (existingChat != null) {
            // Send the initial message if provided
            if (request.getMessage() != null && !request.getMessage().trim().isEmpty()) {
                SendMessageRequest msgRequest = SendMessageRequest.builder()
                        .chatId(existingChat.getId())
                        .content(request.getMessage())
                        .messageType(request.getMessageType() != null ? request.getMessageType() : "TEXT")
                        .metadata(request.getMetadata())
                        .build();
                sendMessage(msgRequest, buyerId);
            }
            return convertToRoomDto(existingChat);
        }

        // Calculate initial lead score
        int leadScore = calculateInitialLeadScore(buyer, request.getMessage());

        String chatName = car.getMake() + " " + car.getModel() + " - " + buyer.getFirstName();

        ChatRoom chatRoom = ChatRoom.builder()
                .name(chatName)
                .description("Car inquiry chat for " + car.getFullName())
                .type(ChatRoom.ChatType.CAR_INQUIRY)
                .createdBy(buyer)
                .car(car)
                .isActive(true)
                .status(ChatRoom.InquiryStatus.NEW)
                .priority(ChatRoom.InquiryPriority.MEDIUM)
                .leadScore(leadScore)
                .buyerName(buyer.getDisplayName())
                .buyerPhone(buyer.getPhoneNumber()) // Assuming User has this field, if not handles graceful null
                .buyerEmail(buyer.getEmail())
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
     * Get Dealer Inquiries
     */
    @Transactional(readOnly = true)
    public Page<ChatRoomDto> getDealerInquiries(Long dealerId, String status, Pageable pageable) {
        log.info("Fetching inquiries for dealer ID: {}", dealerId);

        // Find chats where:
        // 1. Type is CAR_INQUIRY
        // 2. Car belongs to dealer (or dealer is a participant - specifically seller)
        // Ideally rely on repository method

        ChatRoom.InquiryStatus inquiryStatus = null;
        if (status != null && !status.equalsIgnoreCase("ALL")) {
            try {
                inquiryStatus = ChatRoom.InquiryStatus.valueOf(status.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore invalid status
            }
        }

        Page<ChatRoom> inquiries;
        if (inquiryStatus != null) {
            inquiries = chatRoomRepository.findDealerInquiriesByStatus(dealerId, inquiryStatus, pageable);
        } else {
            inquiries = chatRoomRepository.findDealerInquiries(dealerId, pageable);
        }

        return inquiries.map(this::convertToRoomDto);
    }

    /**
     * Update Inquiry Status
     */
    public ChatRoomDto updateInquiryStatus(Long chatId, String status, Long dealerId) {
        log.info("Updating status for chat ID: {} to {}", chatId, status);

        ChatRoom chatRoom = getChatRoomById(chatId);

        // Verify dealer owns the car (security check)
        if (chatRoom.getCar() == null || !chatRoom.getCar().getOwner().getId().equals(dealerId)) {
            throw new AccessDeniedException("You can only manage inquiries for your own cars");
        }

        try {
            ChatRoom.InquiryStatus newStatus = ChatRoom.InquiryStatus.valueOf(status.toUpperCase());
            chatRoom.setStatus(newStatus);

            // Adjust lead score based on status progress
            if (newStatus == ChatRoom.InquiryStatus.CONTACTED) {
                chatRoom.setLeadScore(Math.min(100, chatRoom.getLeadScore() + 10));
            } else if (newStatus == ChatRoom.InquiryStatus.INTERESTED) {
                chatRoom.setLeadScore(Math.min(100, chatRoom.getLeadScore() + 20));
            }

            chatRoom = chatRoomRepository.save(chatRoom);
            return convertToRoomDto(chatRoom);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("Invalid inquiry status");
        }
    }

    private int calculateInitialLeadScore(User buyer, String message) {
        int score = 50; // Base score

        // Bonus for verified email
        if (Boolean.TRUE.equals(buyer.getIsEmailVerified())) {
            score += 10;
        }

        // Bonus for having phone number (dummy check as field might not exist on User
        // entity yet)
        if (buyer.getPhoneNumber() != null && !buyer.getPhoneNumber().isEmpty()) {
            score += 20;
        }

        // Message length analysis
        if (message != null && message.length() > 50) {
            score += 5;
        }

        return Math.min(100, score);
    }

    /**
     * Get messages in chat
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageDto> getChatMessages(Long chatId, Long userId, Pageable pageable) {
        log.info("Getting messages for chat ID: {} by user ID: {}", chatId, userId);

        chatAuthorizationService.assertCanViewChat(userId, chatId);

        // Optimization: Cache first page of messages
        // We only cache page 0 as it's the most high-frequency read interaction
        if (pageable.getPageNumber() == 0) {
            return getCachedRecentMessages(chatId, pageable);
        }

        return fetchMessagesFromDb(chatId, pageable);
    }

    /**
     * Cacheable method for recent messages (Page 0)
     */
    @org.springframework.cache.annotation.Cacheable(value = "recentChatMessages", key = "#chatId")
    public Page<ChatMessageDto> getCachedRecentMessages(Long chatId, Pageable pageable) {
        return fetchMessagesFromDb(chatId, pageable);
    }

    private Page<ChatMessageDto> fetchMessagesFromDb(Long chatId, Pageable pageable) {
        Page<ChatMessage> messages = chatMessageRepository
                .findByChatRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(chatId, pageable);
        return messages.map(this::convertToMessageDto);
    }

    /**
     * Send message to chat
     */
    public ChatMessageDto sendMessage(Long chatId, String content, Long senderId) {
        return sendMessage(SendMessageRequest.builder()
                .chatId(chatId)
                .content(content)
                .messageType("TEXT")
                .build(), senderId);
    }

    /**
     * Send message with full request
     */
    public ChatMessageDto sendMessage(SendMessageRequest request, Long senderId) {
        log.info("Sending message to chat ID: {} from user ID: {}", request.getChatId(), senderId);

        User sender = getUserById(senderId);
        ChatRoom chatRoom = chatAuthorizationService.assertCanSendMessage(senderId, request.getChatId());

        // Handle File Finalization (Direct Upload Flow)
        if (request.getTempFileId() != null) {
            log.info("Finalizing file upload for temp ID: {}", request.getTempFileId());
            try {
                List<UploadedFile> finalizedFiles = mediaFinalizationService.finalizeUploads(
                        List.of(request.getTempFileId()),
                        "chat/" + request.getChatId(),
                        ResourceType.CHAT_ATTACHMENT,
                        request.getChatId(),
                        sender);

                if (!finalizedFiles.isEmpty()) {
                    UploadedFile file = finalizedFiles.get(0);
                    request.setFileUrl(file.getFileUrl());
                    request.setFileName(file.getOriginalFileName());
                    request.setFileSize(file.getSize());
                    request.setMimeType(file.getContentType());

                    // Auto-detect message type if not set or generic
                    if (request.getMessageType() == null || request.getMessageType().equals("TEXT")) {
                        String mime = file.getContentType().toLowerCase();
                        if (mime.startsWith("image/")) {
                            request.setMessageType("IMAGE");
                        } else if (mime.startsWith("video/")) {
                            request.setMessageType("FILE"); // Or VIDEO if supported
                        } else {
                            request.setMessageType("FILE");
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to finalize chat file: {}", e.getMessage());
                throw new BusinessException("Failed to process attached file");
            }
        }

        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            ChatMessage existing = chatMessageRepository
                    .findBySenderIdAndClientMessageId(senderId, request.getClientMessageId())
                    .orElse(null);
            if (existing != null && existing.getChatRoom() != null
                    && existing.getChatRoom().getId().equals(request.getChatId())) {
                return convertToMessageDto(existing);
            }
        }

        ChatMessage.MessageType messageType = ChatMessage.MessageType.TEXT;
        if (request.getMessageType() != null) {
            try {
                // Allow explicit type setting (e.g. for CAR_REFERENCE, LOCATION)
                messageType = ChatMessage.MessageType.valueOf(request.getMessageType().toUpperCase());
            } catch (IllegalArgumentException e) {
                // Default to TEXT
            }
        }

        ChatMessage message = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .content(request.getContent())
                .messageType(messageType)
                .clientMessageId(request.getClientMessageId())
                .build();

        // Handle reply
        if (request.getReplyToId() != null) {
            ChatMessage replyToMessage = chatMessageRepository.findById(request.getReplyToId())
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

        ChatMessageDto messageDto = convertToMessageDto(message);

        // Send real-time message
        sendRealTimeMessage(chatRoom, messageDto);

        // Evict cache for recent messages
        evictRecentMessagesCache(chatRoom.getId());

        log.info("Message sent with ID: {}", message.getId());
        return messageDto;
    }

    @org.springframework.cache.annotation.CacheEvict(value = "recentChatMessages", key = "#chatId")
    public void evictRecentMessagesCache(Long chatId) {
        log.debug("Evicting recent messages cache for chat: {}", chatId);
    }

    /**
     * Edit message
     */
    public ChatMessageDto editMessage(Long messageId, String newContent, Long userId) {
        log.info("Editing message ID: {} by user ID: {}", messageId, userId);

        ChatMessage message = chatMessageRepository.findById(messageId)
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

        ChatMessageDto messageDto = convertToMessageDto(message);

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

        ChatMessage message = chatMessageRepository.findById(messageId)
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

        ChatRoom chatRoom = chatAuthorizationService.assertCanViewChat(userId, chatId);

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
        List<ChatMessage> messages = chatMessageRepository.findAllById(messageIds);
        List<ChatMessage> updatedMessages = messages.stream()
                .filter(msg -> !msg.getSender().getId().equals(userId))
                .filter(msg -> msg.getDeliveryStatus() != ChatMessage.DeliveryStatus.READ)
                .peek(msg -> msg.setDeliveryStatus(ChatMessage.DeliveryStatus.READ))
                .collect(Collectors.toList());

        if (!updatedMessages.isEmpty()) {
            chatMessageRepository.saveAll(updatedMessages);

            // Send real-time updates for read messages
            for (ChatMessage msg : updatedMessages) {
                sendRealTimeMessageUpdate(chatRoom, convertToMessageDto(msg), "READ");
            }
        }

        log.info("Messages marked as read successfully");
    }

    /**
     * Search messages in chat
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageDto> searchMessagesInChat(Long chatId, String query, Long userId, Pageable pageable) {
        log.info("Searching messages in chat ID: {} for query: '{}' by user ID: {}", chatId, query, userId);

        chatAuthorizationService.assertCanViewChat(userId, chatId);

        Page<ChatMessage> messages = chatMessageRepository
                .searchInChat(chatId, query, pageable);

        return messages.map(this::convertToMessageDto);
    }

    /**
     * Get chat participants
     */
    @Transactional(readOnly = true)
    public List<ChatParticipantDto> getChatParticipants(Long chatId, Long userId) {
        log.info("Getting participants for chat ID: {} by user ID: {}", chatId, userId);

        chatAuthorizationService.assertCanViewChat(userId, chatId);

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

        ChatRoom chatRoom = chatAuthorizationService.assertCanViewChat(adminUserId, chatId);
        chatAuthorizationService.assertIsAdmin(adminUserId, chatId);

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

        ChatRoom chatRoom = chatAuthorizationService.assertCanViewChat(adminUserId, chatId);
        chatAuthorizationService.assertIsAdmin(adminUserId, chatId);

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
                        row -> (Long) row[0], // chat_id
                        row -> (Long) row[1] // unread_count
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

        ChatRoom chatRoom = chatAuthorizationService.assertCanSendMessage(userId, chatId);
        User uploader = getUserById(userId);

        try {
            // Upload file using the file upload service
            // Use strict upload method with ResourceType.CHAT_ATTACHMENT
            FileUploadResponse response = fileUploadService.uploadFile(file, "chat/" + chatRoom.getId(), uploader,
                    ResourceType.CHAT_ATTACHMENT, userId);

            log.info("File uploaded successfully: {}", response.getFileName());
            return response;
        } catch (IOException e) {
            log.error("Failed to upload file to chat: {}", e.getMessage());
            throw new RuntimeException("Failed to upload file: " + e.getMessage(), e);
        }
    }

    /**
     * Invite user to group chat
     */
    public void inviteUserToGroup(Long chatId, String username, Long inviterId) {
        log.info("Inviting user {} to chat ID: {} by inviter ID: {}", username, chatId, inviterId);

        ChatRoom chatRoom = chatAuthorizationService.assertCanViewChat(inviterId, chatId);
        chatAuthorizationService.assertIsAdmin(inviterId, chatId);

        if (chatRoom.getType() != ChatRoom.ChatType.GROUP && chatRoom.getType() != ChatRoom.ChatType.DEALER_NETWORK) {
            throw new BusinessException("Invites only supported for group chats");
        }

        User invitee = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));

        // Check if already a participant
        if (chatParticipantRepository.findByChatRoomIdAndUserId(chatId, invitee.getId()).isPresent()) {
            throw new BusinessException("User is already a participant");
        }

        // Check if invite already exists
        if (chatGroupInviteRepository.existsByChatRoomIdAndInviteeId(chatId, invitee.getId())) {
            throw new BusinessException("Invite already pending for this user");
        }

        ChatGroupInvite invite = ChatGroupInvite.builder()
                .chatRoom(chatRoom)
                .inviter(getUserById(inviterId))
                .invitee(invitee)
                .build();

        chatGroupInviteRepository.save(invite);
        log.info("Invite sent successfully");
    }

    /**
     * Accept group invite
     */
    public void acceptGroupInvite(Long inviteId, Long userId) {
        log.info("Accepting invite ID: {} by user ID: {}", inviteId, userId);

        ChatGroupInvite invite = chatGroupInviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (!invite.getInvitee().getId().equals(userId)) {
            throw new AccessDeniedException("This invite is not for you");
        }

        // Add to group
        addParticipant(invite.getChatRoom(), invite.getInvitee(), ChatParticipant.ParticipantRole.MEMBER);

        // Delete invite data as requested
        chatGroupInviteRepository.delete(invite);
        log.info("Invite accepted and deleted");
    }

    /**
     * Reject group invite
     */
    public void rejectGroupInvite(Long inviteId, Long userId) {
        log.info("Rejecting invite ID: {} by user ID: {}", inviteId, userId);

        ChatGroupInvite invite = chatGroupInviteRepository.findById(inviteId)
                .orElseThrow(() -> new ResourceNotFoundException("Invite not found"));

        if (!invite.getInvitee().getId().equals(userId)) {
            throw new AccessDeniedException("This invite is not for you");
        }

        // Delete invite data
        chatGroupInviteRepository.delete(invite);
        log.info("Invite rejected and deleted");
    }

    /**
     * Get pending invites for user
     */
    @Transactional(readOnly = true)
    public List<GroupInviteDto> getPendingInvites(Long userId) {
        return chatGroupInviteRepository.findByInviteeId(userId).stream()
                .map(invite -> GroupInviteDto.builder()
                        .id(invite.getId())
                        .chatRoomId(invite.getChatRoom().getId())
                        .chatRoomName(invite.getChatRoom().getName())
                        .inviterId(invite.getInviter().getId())
                        .inviterName(invite.getInviter().getDisplayName())
                        .createdAt(invite.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * Search dealers by username (for invites)
     */
    @Transactional(readOnly = true)
    public List<ChatParticipantDto> searchDealers(String query) {
        // This relies on a new repository method or filtering existing one
        // For simplicity, reusing searchUsers and filtering in stream ifrepo support
        // missing
        // But better to add specific query in UserRepository.
        // Falling back to searchUsers for now and filtering by role
        return userRepository.searchUsers(query).stream()
                .filter(u -> u.getRole() == Role.DEALER)
                .map(user -> ChatParticipantDto.builder()
                        .user(ChatParticipantDto.UserInfo.builder()
                                .id(user.getId())
                                .username(user.getUsername())
                                .email(user.getEmail())
                                .displayName(user.getDisplayName())
                                .build())
                        .role(ChatParticipant.ParticipantRole.MEMBER.name())
                        .isActive(true)
                        .build())
                .collect(Collectors.toList());
    }

    // Private helper methods

    private ChatRoom getChatRoomById(Long chatId) {
        return chatRoomRepository.findById(chatId)
                .orElseThrow(() -> new ResourceNotFoundException("Chat room not found with ID: " + chatId));
    }

    private User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: " + userId));
    }

    public boolean isParticipant(Long chatId, Long userId) {
        return chatParticipantRepository
                .findByChatRoomIdAndUserIdAndIsActiveTrue(chatId, userId)
                .isPresent();
    }

    public boolean hasAccessToChat(ChatRoom chatRoom, Long userId) {
        return isParticipant(chatRoom.getId(), userId);
    }

    private void addParticipant(ChatRoom chatRoom, User user, ChatParticipant.ParticipantRole role) {
        ChatParticipant participant = ChatParticipant.builder()
                .chatRoom(chatRoom)
                .user(user)
                .role(role)
                .isActive(true)
                .build();
        chatParticipantRepository.save(participant);
    }

    private void updateParticipantActivity(ChatRoom chatRoom, Long userId) {
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

    private void sendRealTimeMessage(ChatRoom chatRoom, ChatMessageDto messageDto) {
        // Publish Async Event instead of blocking WebSocket send
        eventPublisher
                .publishEvent(new com.carselling.oldcar.event.ChatMessageEvent(this, messageDto, chatRoom.getId()));
    }

    private void sendRealTimeMessageUpdate(ChatRoom chatRoom, ChatMessageDto messageDto, String action) {
        MessageUpdateDto updateDto = MessageUpdateDto.builder()
                .action(action)
                .message(messageDto)
                .timestamp(LocalDateTime.now())
                .build();

        // Publish Async Event for Update
        eventPublisher
                .publishEvent(new com.carselling.oldcar.event.ChatMessageEvent(this, updateDto, chatRoom.getId()));
    }

    private void sendSystemMessage(ChatRoom chatRoom, String content) {
        ChatMessage systemMessage = ChatMessage.builder()
                .chatRoom(chatRoom)
                .sender(null) // System message
                .content(content)
                .messageType(ChatMessage.MessageType.SYSTEM)
                .build();

        systemMessage = chatMessageRepository.save(systemMessage);

        ChatMessageDto messageDto = convertToMessageDto(systemMessage);
        sendRealTimeMessage(chatRoom, messageDto);
    }

    // DTO conversion methods

    private ChatRoomDto convertToRoomDto(ChatRoom chatRoom) {
        // Fallback to DB fetch for single item (legacy behavior for single fetches)
        ChatMessage lastMessage = chatMessageRepository
                .findFirstByChatRoomIdOrderByCreatedAtDesc(chatRoom.getId())
                .orElse(null);
        return convertToRoomDto(chatRoom, lastMessage);
    }

    private ChatMessageDto convertToMessageDto(ChatMessage message) {
        ChatMessageDto.ChatMessageDtoBuilder builder = ChatMessageDto.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoom().getId())
                .sender(message.getSender() != null ? ChatMessageDto.Sender.builder()
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
            builder.replyTo(ChatMessageDto.ReplyTo.builder()
                    .id(message.getReplyTo().getId())
                    .content(message.getReplyTo().getContent())
                    .senderUsername(
                            message.getReplyTo().getSender() != null ? message.getReplyTo().getSender().getUsername()
                                    : "System")
                    .messageType(message.getReplyTo().getMessageType().name())
                    .build());
        }

        // Handle file attachment
        if (message.getFileUrl() != null) {
            builder.fileAttachment(ChatMessageDto.FileAttachment.builder()
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
    public ChatRoomDto updateChatRoom(Long chatId, UpdateChatRoomRequest request, Long userId) {
        log.info("Updating chat room ID: {} by user ID: {}", chatId, userId);

        ChatRoom chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }

        // Check if user is admin (for group chats)
        if (chatRoom.getType() == ChatRoom.ChatType.GROUP) {
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
    public ChatMessageDto sendFileMessage(Long chatId, MultipartFile file, String content, Long replyToId,
            Long senderId) {
        log.info("Sending file message to chat ID: {} by user ID: {}", chatId, senderId);

        // Upload file first
        FileUploadResponse uploadResponse = uploadChatFile(file, chatId, senderId);

        // Create message with file info
        SendMessageRequest request = SendMessageRequest.builder()
                .chatId(chatId)
                .content(content != null ? content : "Sent a file: " + uploadResponse.getFileName())
                .messageType("FILE")
                .replyToId(replyToId)
                .build();

        ChatMessageDto message = sendMessage(request, senderId);

        // Update message with file details (this would normally be done in the entity)
        // For now, we'll return the message as is
        return message;
    }

    /**
     * Search messages across all user's chats
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageDto> searchMessages(String query, Long userId, Pageable pageable) {
        log.info("Searching messages for query: '{}' by user ID: {}", query, userId);

        /*
         * Search across all chats the user is a participant of.
         * Using the optimized repository method that joins participants.
         */
        Page<ChatMessage> messages = chatMessageRepository.searchUserChats(userId, query, pageable);

        return messages.map(this::convertToMessageDto);
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
    public Page<ChatRoomDto> getDealerGroups(Long userId, Pageable pageable) {
        log.info("Getting dealer groups for user ID: {}", userId);

        // This would filter for dealer-specific group chats
        // For now, return all group chats the user is part of
        Page<ChatRoom> dealerGroups = chatRoomRepository
                .findByParticipantUserIdAndType(userId, ChatRoom.ChatType.GROUP, pageable);

        return dealerGroups.map(this::convertToRoomDto);
    }

    /**
     * Create dealer group
     */
    public ChatRoomDto createDealerGroup(Long userId, CreateDealerGroupRequest request) {
        log.info("Creating dealer group by user ID: {}", userId);

        // Convert to CreateGroupChatRequest and use existing method
        CreateGroupChatRequest groupRequest = CreateGroupChatRequest.builder()
                .name(request.getName())
                .description(request.getDescription())
                .type("GROUP")
                .maxParticipants(50) // Default max participants for dealer groups
                .participantIds(request.getDealerIds() != null ? request.getDealerIds() : java.util.List.of()) // Use
                                                                                                               // dealerIds
                                                                                                               // as
                                                                                                               // participantIds
                .build();

        return createGroupChat(groupRequest, userId);
    }

    /**
     * Send typing indicator
     */
    public void sendTypingIndicator(Long chatId, Long userId, boolean isTyping) {
        log.info("User ID: {} {} typing in chat ID: {}", userId, isTyping ? "started" : "stopped", chatId);

        ChatRoom chatRoom = getChatRoomById(chatId);
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
    public ChatMessageDto getChatMessage(Long messageId, Long userId) {
        log.info("Getting message ID: {} by user ID: {}", messageId, userId);

        ChatMessage message = chatMessageRepository.findById(messageId)
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

    public Page<ChatMessageDto> searchMessagesInChatRoom(Long chatId, String query, Long userId, Pageable pageable) {
        log.info("Searching messages in chat ID: {} for query: '{}' by user ID: {}", chatId, query, userId);

        ChatRoom chatRoom = getChatRoomById(chatId);
        if (!hasAccessToChat(chatRoom, userId)) {
            throw new AccessDeniedException("Access denied to chat room");
        }

        return searchMessagesInChat(chatId, query, userId, pageable);
    }
}
