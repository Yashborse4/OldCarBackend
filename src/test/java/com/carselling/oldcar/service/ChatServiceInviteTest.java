package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.chat.ChatRoomDto;
import com.carselling.oldcar.exception.BusinessException;
import com.carselling.oldcar.model.ChatInviteLink;
import com.carselling.oldcar.model.ChatParticipant;
import com.carselling.oldcar.model.ChatRoom;
import com.carselling.oldcar.model.ChatRoom.ChatType;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.ChatInviteLinkRepository;
import com.carselling.oldcar.repository.ChatParticipantRepository;
import com.carselling.oldcar.repository.ChatRoomRepository;
import com.carselling.oldcar.repository.UserRepository;
import com.carselling.oldcar.model.ChatParticipant.ParticipantRole;
import com.carselling.oldcar.service.chat.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceInviteTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChatParticipantRepository chatParticipantRepository;
    @Mock
    private ChatInviteLinkRepository chatInviteLinkRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatService chatService;

    private User adminUser;
    private User normalUser;
    private ChatRoom groupChat;
    private ChatParticipant adminParticipant;

    @BeforeEach
    void setUp() {
        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");

        normalUser = new User();
        normalUser.setId(2L);
        normalUser.setUsername("user");

        groupChat = new ChatRoom();
        groupChat.setId(10L);
        groupChat.setName("Test Group");
        groupChat.setType(ChatType.GROUP);

        adminParticipant = new ChatParticipant();
        adminParticipant.setUser(adminUser);
        adminParticipant.setChatRoom(groupChat);
        adminParticipant.setRole(ParticipantRole.ADMIN);
    }

    @Test
    void createInviteLink_ShouldGenerateNewToken_WhenNoneExists() {
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(chatParticipantRepository.findByChatRoomIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminParticipant));
        when(chatInviteLinkRepository.findByChatRoomId(10L)).thenReturn(Optional.empty());
        when(chatInviteLinkRepository.save(any(ChatInviteLink.class))).thenAnswer(i -> i.getArguments()[0]);

        String token = chatService.createInviteLink(10L, 1L);

        assertNotNull(token);
        verify(chatInviteLinkRepository).save(any(ChatInviteLink.class));
    }

    @Test
    void createInviteLink_ShouldReturnExistingToken_WhenValid() {
        ChatInviteLink existingLink = new ChatInviteLink();
        existingLink.setToken("existing-token");
        existingLink.setExpiresAt(LocalDateTime.now().plusDays(1));
        existingLink.setChatRoom(groupChat);

        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(chatParticipantRepository.findByChatRoomIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminParticipant));
        when(chatInviteLinkRepository.findByChatRoomId(10L)).thenReturn(Optional.of(existingLink));

        String token = chatService.createInviteLink(10L, 1L);

        assertEquals("existing-token", token);
        verify(chatInviteLinkRepository, never()).save(any(ChatInviteLink.class));
    }

    @Test
    void createInviteLink_ShouldFail_IfUserNotAdmin() {
        adminParticipant.setRole(ParticipantRole.MEMBER);
        when(chatRoomRepository.findById(10L)).thenReturn(Optional.of(groupChat));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(chatParticipantRepository.findByChatRoomIdAndUserId(10L, 1L)).thenReturn(Optional.of(adminParticipant));

        assertThrows(BusinessException.class, () -> chatService.createInviteLink(10L, 1L));
    }

    @Test
    void joinChatByLink_ShouldAddUser_WhenTokenValid() {
        ChatInviteLink link = new ChatInviteLink();
        link.setToken("valid-token");
        link.setExpiresAt(LocalDateTime.now().plusDays(1));
        link.setChatRoom(groupChat);

        when(chatInviteLinkRepository.findByToken("valid-token")).thenReturn(Optional.of(link));
        when(userRepository.findById(2L)).thenReturn(Optional.of(normalUser));
        when(chatParticipantRepository.findByChatRoomIdAndUserId(10L, 2L)).thenReturn(Optional.empty());

        ChatRoomDto result = chatService.joinChatByLink("valid-token", 2L);

        assertNotNull(result);
        assertEquals(10L, result.getId());
        verify(chatParticipantRepository).save(any(ChatParticipant.class));
    }

    @Test
    void joinChatByLink_ShouldFail_WhenTokenExpired() {
        ChatInviteLink link = new ChatInviteLink();
        link.setToken("expired-token");
        link.setExpiresAt(LocalDateTime.now().minusDays(1));
        link.setChatRoom(groupChat);

        when(chatInviteLinkRepository.findByToken("expired-token")).thenReturn(Optional.of(link));

        assertThrows(BusinessException.class, () -> chatService.joinChatByLink("expired-token", 2L));
    }
}
