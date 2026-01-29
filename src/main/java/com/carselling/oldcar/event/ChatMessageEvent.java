package com.carselling.oldcar.event;

import com.carselling.oldcar.dto.chat.ChatMessageDto;
import com.carselling.oldcar.dto.chat.MessageUpdateDto;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Event for Real-Time Chat messages.
 * Processed asynchronously to push to WebSockets.
 */
@Getter
public class ChatMessageEvent extends ApplicationEvent {

    private final ChatMessageDto messageDto;
    private final MessageUpdateDto updateDto; // For edits/deletes/reads
    private final Long chatRoomId;
    private final EventType type;

    public enum EventType {
        NEW_MESSAGE,
        UPDATE_MESSAGE
    }

    // Constructor for New Message
    public ChatMessageEvent(Object source, ChatMessageDto messageDto, Long chatRoomId) {
        super(source);
        this.messageDto = messageDto;
        this.updateDto = null;
        this.chatRoomId = chatRoomId;
        this.type = EventType.NEW_MESSAGE;
    }

    // Constructor for Updates
    public ChatMessageEvent(Object source, MessageUpdateDto updateDto, Long chatRoomId) {
        super(source);
        this.updateDto = updateDto;
        this.messageDto = null;
        this.chatRoomId = chatRoomId;
        this.type = EventType.UPDATE_MESSAGE;
    }
}
