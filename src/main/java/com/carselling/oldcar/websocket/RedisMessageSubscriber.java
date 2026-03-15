package com.carselling.oldcar.websocket;

import com.carselling.oldcar.dto.chat.ChatMessageDto;
import com.carselling.oldcar.dto.chat.MessageUpdateDto;
import com.carselling.oldcar.dto.chat.ReadReceiptDto;
import com.carselling.oldcar.dto.chat.RedisChatMessage;
import com.carselling.oldcar.dto.chat.TypingIndicatorDto;
import com.carselling.oldcar.dto.chat.UnreadCountResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.Map;

/**
 * Subscriber that listens to Redis "chat_sync" channel and forwards messages
 * to local WebSocket subscribers. This enables real-time updates in a clustered environment.
 */
@Component
@Slf4j
public class RedisMessageSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RedisMessageSubscriber(SimpMessagingTemplate messagingTemplate, 
                                @Qualifier("redisObjectMapper") ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Callback when a message is received from Redis as a JSON string.
     */
    public void onMessage(String messageJson) {
        try {
            log.debug("Received Redis sync JSON: {}", messageJson);
            RedisChatMessage redisMessage = objectMapper.readValue(messageJson, RedisChatMessage.class);
            
            log.debug("Processed Redis sync message: type={}, room={}", redisMessage.getType(), redisMessage.getChatRoomId());

            String type = redisMessage.getType();
            Long chatRoomId = redisMessage.getChatRoomId();
            Object payload = redisMessage.getPayload();

            switch (type) {
                case "MESSAGE":
                    ChatMessageDto message = objectMapper.convertValue(payload, ChatMessageDto.class);
                    messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/messages", message);
                    break;
                    
                case "UPDATE":
                    MessageUpdateDto update = objectMapper.convertValue(payload, MessageUpdateDto.class);
                    messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/updates", update);
                    break;
                    
                case "TYPING":
                    TypingIndicatorDto typing = objectMapper.convertValue(payload, TypingIndicatorDto.class);
                    messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/typing", typing);
                    break;
                    
                case "READ":
                    ReadReceiptDto receipt = objectMapper.convertValue(payload, ReadReceiptDto.class);
                    messagingTemplate.convertAndSend("/topic/chat/" + chatRoomId + "/read", receipt);
                    break;

                case "UNREAD_COUNT":
                    // payload is Map with "userId" and "count"
                    Map<String, Object> unreadData = (Map<String, Object>) payload;
                    Long userId = Long.valueOf(unreadData.get("userId").toString());
                    UnreadCountResponse countResponse = objectMapper.convertValue(unreadData.get("unreadCount"), UnreadCountResponse.class);
                    
                    messagingTemplate.convertAndSendToUser(
                            userId.toString(),
                            "/queue/unread-count",
                            countResponse);
                    break;

                default:
                    log.warn("Unknown Redis message type: {}", type);
            }
        } catch (Exception e) {
            log.error("Error processing Redis sync message: {}", e.getMessage(), e);
        }
    }
}
