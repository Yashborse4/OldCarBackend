package com.carselling.oldcar.dto.chat;

import java.util.List;

/**
 * DTO for read receipts in WebSocket communication
 */
public class ReadReceiptDto {

    private Long chatRoomId;
    private Long userId;
    private String userName;
    private List<Long> messageIds;
    private Long timestamp;

    // Constructors
    public ReadReceiptDto() {
        this.timestamp = System.currentTimeMillis();
    }

    public ReadReceiptDto(Long chatRoomId, Long userId, String userName, List<Long> messageIds) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.userName = userName;
        this.messageIds = messageIds;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public Long getChatRoomId() {
        return chatRoomId;
    }

    public void setChatRoomId(Long chatRoomId) {
        this.chatRoomId = chatRoomId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public List<Long> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<Long> messageIds) {
        this.messageIds = messageIds;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ReadReceiptDto{" +
                "chatRoomId=" + chatRoomId +
                ", userId=" + userId +
                ", userName='" + userName + '\'' +
                ", messageIds=" + messageIds +
                ", timestamp=" + timestamp +
                '}';
    }
}
