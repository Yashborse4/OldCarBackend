package com.carselling.oldcar.controller;

import com.carselling.oldcar.dto.chat.CreatePrivateChatRequest;
import com.carselling.oldcar.dto.chat.SendMessageRequestV2;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.service.ChatServiceV2;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for ChatControllerV2
 */
@WebMvcTest(ChatControllerV2.class)
class ChatControllerV2IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatServiceV2 chatServiceV2;

    @Autowired
    private ObjectMapper objectMapper;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("testuser");
        mockUser.setEmail("test@example.com");

        // Set up security context
        UsernamePasswordAuthenticationToken auth = 
            new UsernamePasswordAuthenticationToken(mockUser, null, null);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void testCreatePrivateChat_ValidRequest_ReturnsOk() throws Exception {
        // Arrange
        CreatePrivateChatRequest request = new CreatePrivateChatRequest();
        request.setTargetUserId(2L);
        request.setInitialMessage("Hello!");

        // Act & Assert
        mockMvc.perform(post("/api/v2/chat/private")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testCreatePrivateChat_InvalidRequest_ReturnsBadRequest() throws Exception {
        // Arrange
        CreatePrivateChatRequest request = new CreatePrivateChatRequest();
        // Missing required targetUserId

        // Act & Assert
        mockMvc.perform(post("/api/v2/chat/private")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testSendMessage_ValidRequest_ReturnsOk() throws Exception {
        // Arrange
        SendMessageRequestV2 request = new SendMessageRequestV2();
        request.setContent("Test message");
        request.setMessageType("TEXT");

        // Act & Assert
        mockMvc.perform(post("/api/v2/chat/rooms/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    void testSendMessage_EmptyContent_ReturnsBadRequest() throws Exception {
        // Arrange
        SendMessageRequestV2 request = new SendMessageRequestV2();
        request.setContent(""); // Empty content
        request.setMessageType("TEXT");

        // Act & Assert
        mockMvc.perform(post("/api/v2/chat/rooms/1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetUserChatRooms_ValidRequest_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v2/chat/rooms"))
                .andExpect(status().isOk());
    }

    @Test
    void testGetChatMessages_ValidRequest_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v2/chat/rooms/1/messages"))
                .andExpect(status().isOk());
    }

    @Test
    void testSearchMessages_ValidQuery_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v2/chat/search/messages")
                .param("query", "test"))
                .andExpect(status().isOk());
    }

    @Test
    void testSearchMessages_EmptyQuery_ReturnsBadRequest() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v2/chat/search/messages")
                .param("query", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetUnreadCount_ValidRequest_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v2/chat/unread-count"))
                .andExpect(status().isOk());
    }

    @Test
    void testEditMessage_ValidRequest_ReturnsOk() throws Exception {
        // Arrange
        String requestBody = "{\"newContent\":\"Edited message\"}";

        // Act & Assert
        mockMvc.perform(put("/api/v2/chat/messages/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
                .andExpect(status().isOk());
    }

    @Test
    void testDeleteMessage_ValidRequest_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(delete("/api/v2/chat/messages/1"))
                .andExpect(status().isOk());
    }

    @Test
    void testLeaveChatRoom_ValidRequest_ReturnsOk() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/v2/chat/rooms/1/leave"))
                .andExpect(status().isOk());
    }
}
