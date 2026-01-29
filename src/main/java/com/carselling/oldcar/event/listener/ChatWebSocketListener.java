package com.carselling.oldcar.event.listener;

import com.carselling.oldcar.dto.chat.ChatMessageDto;
import com.carselling.oldcar.event.ChatMessageEvent;
import com.carselling.oldcar.model.ChatParticipant;
import com.carselling.oldcar.repository.ChatParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatParticipantRepository chatParticipantRepository;

    @Async
    @EventListener
    @Transactional(readOnly = true)
    public void handleChatMessageEvent(ChatMessageEvent event) {
        try {
            if (event.getType() == ChatMessageEvent.EventType.NEW_MESSAGE) {
                sendRealTimeMessage(event.getChatRoomId(), event.getMessageDto());
            } else if (event.getType() == ChatMessageEvent.EventType.UPDATE_MESSAGE) {
                sendRealTimeUpdate(event.getChatRoomId(), event.getUpdateDto());
            }
        } catch (Exception e) {
            log.error("Failed to send WebSocket message for chat {}: {}", event.getChatRoomId(), e.getMessage(), e);
        }
    }

    private void sendRealTimeMessage(Long chatRoomId, ChatMessageDto messageDto) {
        // Send to individual participants (private user queues)
        List<ChatParticipant> participants = chatParticipantRepository
                .findByChatRoomIdAndIsActiveTrue(chatRoomId);

        for (ChatParticipant participant : participants) {
            String destination = "/user/" + participant.getUser().getId() + "/queue/messages";
            messagingTemplate.convertAndSend(destination, messageDto);
        }

        // Send to public topic (for whoever is subscribed to this room)
        String topicDestination = "/topic/chat/" + chatRoomId;
        messagingTemplate.convertAndSend(topicDestination, messageDto);
    }

    private void sendRealTimeUpdate(Long chatRoomId, Object updateDto) {
        String topicDestination = "/topic/chat/" + chatRoomId + "/updates";
        messagingTemplate.convertAndSend(topicDestination, updateDto);
    }
}
