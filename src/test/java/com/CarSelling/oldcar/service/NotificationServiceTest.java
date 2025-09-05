package com.carselling.oldcar.service;

import com.carselling.oldcar.dto.notification.NotificationRequest;
import com.carselling.oldcar.dto.notification.NotificationResponse;
import com.carselling.oldcar.model.User;
import com.carselling.oldcar.repository.UserRepository;
import com.google.firebase.messaging.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for NotificationService
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private User testUser2;
    private NotificationRequest testNotificationRequest;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setFcmToken("test-fcm-token-1");

        testUser2 = new User();
        testUser2.setId(2L);
        testUser2.setUsername("testuser2");
        testUser2.setEmail("test2@example.com");
        testUser2.setFcmToken("test-fcm-token-2");

        testNotificationRequest = NotificationRequest.builder()
                .title("Test Notification")
                .body("This is a test notification")
                .data(Map.of("type", "test", "id", "1"))
                .build();
    }

    @Test
    void testSendNotificationToUser_Success() throws ExecutionException, InterruptedException, FirebaseMessagingException {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(firebaseMessaging.send(any(Message.class))).thenReturn("message-id-123");

        // Act
        CompletableFuture<NotificationResponse> future = notificationService.sendNotificationToUser(1L, testNotificationRequest);
        NotificationResponse result = future.get();

        // Assert
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals("message-id-123", result.getMessageId());
        assertEquals(1, result.getRecipientCount());
        
        verify(messagingTemplate).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void testSendNotificationToUser_UserNotFound() throws ExecutionException, InterruptedException {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        CompletableFuture<NotificationResponse> future = notificationService.sendNotificationToUser(1L, testNotificationRequest);
        NotificationResponse result = future.get();

        // Assert
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertTrue(result.getMessage().contains("User not found"));
        
        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    @Test
    void testSendNotificationToUser_NoFcmToken() throws ExecutionException, InterruptedException {
        // Arrange
        testUser.setFcmToken(null);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        CompletableFuture<NotificationResponse> future = notificationService.sendNotificationToUser(1L, testNotificationRequest);
        NotificationResponse result = future.get();

        // Assert
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertTrue(result.getMessage().contains("No FCM token found"));
        
        verify(firebaseMessaging, never()).send(any(Message.class));
    }

    @Test
    void testSendNotificationToUsers_Success() throws ExecutionException, InterruptedException, FirebaseMessagingException {
        // Arrange
        List<Long> userIds = Arrays.asList(1L, 2L);
        List<User> users = Arrays.asList(testUser, testUser2);
        
        BatchResponse mockBatchResponse = mock(BatchResponse.class);
        when(mockBatchResponse.getSuccessCount()).thenReturn(2);
        
        when(userRepository.findAllById(userIds)).thenReturn(users);
        when(firebaseMessaging.sendMulticast(any(MulticastMessage.class))).thenReturn(mockBatchResponse);

        // Act
        CompletableFuture<NotificationResponse> future = notificationService.sendNotificationToUsers(userIds, testNotificationRequest);
        NotificationResponse result = future.get();

        // Assert
        assertNotNull(result);
        assertTrue(result.getSuccess());
        assertEquals(2, result.getRecipientCount());
        
        verify(firebaseMessaging).sendMulticast(any(MulticastMessage.class));
        verify(messagingTemplate, times(2)).convertAndSendToUser(anyString(), anyString(), any());
    }

    @Test
    void testSendNotificationToUsers_NoValidTokens() throws ExecutionException, InterruptedException {
        // Arrange
        testUser.setFcmToken(null);
        testUser2.setFcmToken("");
        
        List<Long> userIds = Arrays.asList(1L, 2L);
        List<User> users = Arrays.asList(testUser, testUser2);
        
        when(userRepository.findAllById(userIds)).thenReturn(users);

        // Act
        CompletableFuture<NotificationResponse> future = notificationService.sendNotificationToUsers(userIds, testNotificationRequest);
        NotificationResponse result = future.get();

        // Assert
        assertNotNull(result);
        assertFalse(result.getSuccess());
        assertTrue(result.getMessage().contains("No valid FCM tokens found"));
        
        verify(firebaseMessaging, never()).sendMulticast(any(MulticastMessage.class));
    }

    @Test
    void testSendChatMessageNotification() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        notificationService.sendChatMessageNotification(1L, 2L, "John Doe", "Hello there!", 1L, "General Chat");

        // Assert
        // Since this method calls sendNotificationToUser asynchronously, we can't directly verify the result
        // but we can verify that the user repository was called
        verify(userRepository).findById(1L);
    }

    @Test
    void testSendCarInquiryNotification() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        notificationService.sendCarInquiryNotification(1L, 2L, "Jane Smith", "2020 Honda Civic", 1L);

        // Assert
        verify(userRepository).findById(1L);
    }

    @Test
    void testSendCarRecommendationNotification() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        notificationService.sendCarRecommendationNotification(1L, "2021 Toyota Camry", 1L, "$25,000");

        // Assert
        verify(userRepository).findById(1L);
    }

    @Test
    void testSendPriceDropNotification() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        // Act
        notificationService.sendPriceDropNotification(1L, "2020 Honda Civic", 1L, "$22,000", "$20,000");

        // Assert
        verify(userRepository).findById(1L);
    }

    @Test
    void testUpdateUserFCMToken_Success() {
        // Arrange
        String newToken = "new-fcm-token";
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(userRepository.save(any(User.class))).thenReturn(testUser);

        // Act
        notificationService.updateUserFCMToken(1L, newToken);

        // Assert
        verify(userRepository).save(testUser);
        assertEquals(newToken, testUser.getFcmToken());
    }

    @Test
    void testUpdateUserFCMToken_UserNotFound() {
        // Arrange
        String newToken = "new-fcm-token";
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Act
        notificationService.updateUserFCMToken(1L, newToken);

        // Assert
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testNotificationRequest_ChatMessageHelper() {
        // Act
        NotificationRequest request = NotificationRequest.chatMessage("John Doe", "Hello there!", 1L);

        // Assert
        assertNotNull(request);
        assertEquals("New message from John Doe", request.getTitle());
        assertEquals("Hello there!", request.getBody());
        assertTrue(request.getHighPriority());
        assertEquals("default", request.getSound());
        assertEquals("chat_message", request.getData().get("type"));
        assertEquals("1", request.getData().get("chatRoomId"));
    }

    @Test
    void testNotificationRequest_CarInquiryHelper() {
        // Act
        NotificationRequest request = NotificationRequest.carInquiry("Jane Smith", "2020 Honda Civic", 1L);

        // Assert
        assertNotNull(request);
        assertEquals("New inquiry for your car", request.getTitle());
        assertEquals("Jane Smith is interested in 2020 Honda Civic", request.getBody());
        assertTrue(request.getHighPriority());
        assertEquals("car_inquiry", request.getData().get("type"));
        assertEquals("1", request.getData().get("carId"));
    }

    @Test
    void testNotificationRequest_PriceAlertHelper() {
        // Act
        NotificationRequest request = NotificationRequest.priceAlert("2020 Honda Civic", "$18,000", 1L);

        // Assert
        assertNotNull(request);
        assertEquals("Price drop alert!", request.getTitle());
        assertEquals("2020 Honda Civic is now $18,000", request.getBody());
        assertTrue(request.getHighPriority());
        assertEquals("price_drop", request.getData().get("type"));
        assertEquals("1", request.getData().get("carId"));
    }
}
