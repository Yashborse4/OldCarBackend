package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.chat.*;
import com.carselling.oldcar.model.*;
import com.carselling.oldcar.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ChatServiceV2
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceV2Test {

    @Mock
    private ChatRoomV2Repository chatRoomV2Repository;

    @Mock
    private ChatMessageV2Repository chatMessageV2Repository;

    @Mock
    private ChatParticipantRepository chatParticipantRepository;

    @Mock
    private MessageReadRepository messageReadRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CarRepository carRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatServiceV2 chatServiceV2;

    private User testUser;
    private User testUser2;
    private ChatRoomV2 testChatRoom;
    private ChatMessageV2 testMessage;
    private ChatParticipant testParticipant;

    @BeforeEach
    void setUp() {
        // Setup test data
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFullName("Test User");

        testUser2 = new User();
        testUser2.setId(2L);
        testUser2.setUsername("testuser2");
        testUser2.setEmail("test2@example.com");
        testUser2.setFullName("Test User 2");

        testChatRoom = new ChatRoomV2();
        testChatRoom.setId(1L);
        testChatRoom.setName("Test Chat");
        testChatRoom.setType(ChatRoomV2.ChatType.PRIVATE);
        testChatRoom.setCreatedBy(testUser);
        testChatRoom.setCreatedAt(LocalDateTime.now());
        testChatRoom.setIsActive(true);

        testMessage = new ChatMessageV2();
        testMessage.setId(1L);
        testMessage.setContent("Test message");
        testMessage.setChatRoom(testChatRoom);
        testMessage.setSender(testUser);
        testMessage.setMessageType(ChatMessageV2.MessageType.TEXT);
        testMessage.setCreatedAt(LocalDateTime.now());

        testParticipant = new ChatParticipant();
        testParticipant.setId(1L);
        testParticipant.setUser(testUser);
        testParticipant.setChatRoom(testChatRoom);
        testParticipant.setRole(ChatParticipant.ParticipantRole.MEMBER);
        testParticipant.setIsActive(true);
        testParticipant.setJoinedAt(LocalDateTime.now());
    }

    @Test
    void testCreatePrivateChat_Success() {
        // Arrange
        CreatePrivateChatRequest request = new CreatePrivateChatRequest();
        request.setTargetUserId(2L);
        request.setInitialMessage("Hello!");

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(chatRoomV2Repository.findPrivateChatBetweenUsers(1L, 2L)).thenReturn(Optional.empty());
        when(chatRoomV2Repository.save(any(ChatRoomV2.class))).thenReturn(testChatRoom);
        when(chatParticipantRepository.save(any(ChatParticipant.class))).thenReturn(testParticipant);

        // Act
        ChatRoomDtoV2 result = chatServiceV2.createPrivateChat(1L, request);

        // Assert
        assertNotNull(result);
        assertEquals("Test Chat", result.getName());
        verify(chatRoomV2Repository).save(any(ChatRoomV2.class));
        verify(chatParticipantRepository, times(2)).save(any(ChatParticipant.class));
    }

    @Test
    void testCreatePrivateChat_AlreadyExists() {
        // Arrange
        CreatePrivateChatRequest request = new CreatePrivateChatRequest();
        request.setTargetUserId(2L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(chatRoomV2Repository.findPrivateChatBetweenUsers(1L, 2L)).thenReturn(Optional.of(testChatRoom));

        // Act
        ChatRoomDtoV2 result = chatServiceV2.createPrivateChat(1L, request);

        // Assert
        assertNotNull(result);
        verify(chatRoomV2Repository, never()).save(any(ChatRoomV2.class));
    }

    @Test
    void testSendMessage_Success() {
        // Arrange
        SendMessageRequestV2 request = new SendMessageRequestV2();
        request.setContent("Test message");
        request.setMessageType("TEXT");

        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(chatParticipantRepository.isUserActiveParticipant(1L, 1L)).thenReturn(true);
        when(chatMessageV2Repository.save(any(ChatMessageV2.class))).thenReturn(testMessage);

        // Act
        ChatMessageDtoV2 result = chatServiceV2.sendMessage(1L, request, 1L);

        // Assert
        assertNotNull(result);
        assertEquals("Test message", result.getContent());
        verify(chatMessageV2Repository).save(any(ChatMessageV2.class));
        verify(messagingTemplate).convertAndSend(anyString(), any());
    }

    @Test
    void testSendMessage_UserNotParticipant() {
        // Arrange
        SendMessageRequestV2 request = new SendMessageRequestV2();
        request.setContent("Test message");

        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(chatParticipantRepository.isUserActiveParticipant(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            chatServiceV2.sendMessage(1L, request, 1L);
        });
    }

    @Test
    void testGetChatMessages_Success() {
        // Arrange
        Pageable pageable = mock(Pageable.class);
        List<ChatMessageV2> messages = Arrays.asList(testMessage);
        Page<ChatMessageV2> messagePage = new PageImpl<>(messages);

        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(chatParticipantRepository.isUserActiveParticipant(1L, 1L)).thenReturn(true);
        when(chatMessageV2Repository.findByChatRoomIdAndIsDeletedFalse(1L, pageable)).thenReturn(messagePage);

        // Act
        Page<ChatMessageDtoV2> result = chatServiceV2.getChatMessages(1L, 1L, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        assertEquals("Test message", result.getContent().get(0).getContent());
    }

    @Test
    void testEditMessage_Success() {
        // Arrange
        String newContent = "Edited message";
        when(chatMessageV2Repository.findById(1L)).thenReturn(Optional.of(testMessage));
        when(chatMessageV2Repository.save(any(ChatMessageV2.class))).thenReturn(testMessage);

        // Act
        ChatMessageDtoV2 result = chatServiceV2.editMessage(1L, newContent, 1L);

        // Assert
        assertNotNull(result);
        verify(chatMessageV2Repository).save(any(ChatMessageV2.class));
        verify(messagingTemplate).convertAndSend(anyString(), any());
    }

    @Test
    void testEditMessage_NotOwner() {
        // Arrange
        String newContent = "Edited message";
        when(chatMessageV2Repository.findById(1L)).thenReturn(Optional.of(testMessage));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            chatServiceV2.editMessage(1L, newContent, 2L); // Different user ID
        });
    }

    @Test
    void testDeleteMessage_Success() {
        // Arrange
        when(chatMessageV2Repository.findById(1L)).thenReturn(Optional.of(testMessage));
        when(chatMessageV2Repository.softDeleteMessage(eq(1L), eq(1L), any(LocalDateTime.class))).thenReturn(1);

        // Act
        chatServiceV2.deleteMessage(1L, 1L);

        // Assert
        verify(chatMessageV2Repository).softDeleteMessage(eq(1L), eq(1L), any(LocalDateTime.class));
        verify(messagingTemplate).convertAndSend(anyString(), any());
    }

    @Test
    void testMarkMessagesAsRead_Success() {
        // Arrange
        List<Long> messageIds = Arrays.asList(1L, 2L);
        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(chatParticipantRepository.isUserActiveParticipant(1L, 1L)).thenReturn(true);
        when(messageReadRepository.markMessagesAsRead(eq(messageIds), eq(1L), any(LocalDateTime.class))).thenReturn(2);

        // Act
        chatServiceV2.markMessagesAsRead(1L, messageIds, 1L);

        // Assert
        verify(messageReadRepository).markMessagesAsRead(eq(messageIds), eq(1L), any(LocalDateTime.class));
    }

    @Test
    void testGetUnreadMessageCount_Success() {
        // Arrange
        when(chatMessageV2Repository.countTotalUnreadMessagesForUser(1L)).thenReturn(5L);

        // Act
        UnreadCountResponse result = chatServiceV2.getUnreadMessageCount(1L);

        // Assert
        assertNotNull(result);
        assertEquals(5L, result.getTotalUnreadCount());
    }

    @Test
    void testAddParticipants_Success() {
        // Arrange
        List<Long> userIds = Arrays.asList(2L);
        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(chatParticipantRepository.isUserAdminInChatRoom(1L, 1L)).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(testUser2));
        when(chatParticipantRepository.findByUserIdAndChatRoomId(2L, 1L)).thenReturn(Optional.empty());

        // Act
        chatServiceV2.addParticipants(1L, userIds, 1L);

        // Assert
        verify(chatParticipantRepository).save(any(ChatParticipant.class));
    }

    @Test
    void testAddParticipants_NotAdmin() {
        // Arrange
        List<Long> userIds = Arrays.asList(2L);
        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(chatParticipantRepository.isUserAdminInChatRoom(1L, 1L)).thenReturn(false);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> {
            chatServiceV2.addParticipants(1L, userIds, 1L);
        });
    }

    @Test
    void testRemoveParticipant_Success() {
        // Arrange
        when(chatRoomV2Repository.findById(1L)).thenReturn(Optional.of(testChatRoom));
        when(chatParticipantRepository.isUserAdminInChatRoom(1L, 1L)).thenReturn(true);
        when(chatParticipantRepository.deactivateParticipant(eq(2L), eq(1L), any(LocalDateTime.class))).thenReturn(1);

        // Act
        chatServiceV2.removeParticipant(1L, 2L, 1L);

        // Assert
        verify(chatParticipantRepository).deactivateParticipant(eq(2L), eq(1L), any(LocalDateTime.class));
    }

    @Test
    void testSearchMessages_Success() {
        // Arrange
        String query = "test";
        Pageable pageable = mock(Pageable.class);
        List<ChatMessageV2> messages = Arrays.asList(testMessage);
        Page<ChatMessageV2> messagePage = new PageImpl<>(messages);

        when(chatMessageV2Repository.searchMessagesForUser(1L, query, pageable)).thenReturn(messagePage);

        // Act
        Page<ChatMessageDtoV2> result = chatServiceV2.searchMessages(query, 1L, pageable);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getContent().size());
    }
}
