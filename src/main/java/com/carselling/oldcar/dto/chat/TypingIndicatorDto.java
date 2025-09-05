package com.carselling.oldcar.dto.chat;

/**
 * DTO for typing indicators in WebSocket communication
 */
public class TypingIndicatorDto {

    private Long chatRoomId;
    private Long userId;
    private String userName;
    private Boolean isTyping;
    private Long timestamp;

    // Constructors
    public TypingIndicatorDto() {
        this.timestamp = System.currentTimeMillis();
    }

    public TypingIndicatorDto(Long chatRoomId, Long userId, String userName, Boolean isTyping) {
        this.chatRoomId = chatRoomId;
        this.userId = userId;
        this.userName = userName;
        this.isTyping = isTyping;
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

    public Boolean getIsTyping() {
        return isTyping;
    }

    public void setIsTyping(Boolean isTyping) {
        this.isTyping = isTyping;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "TypingIndicatorDto{" +
                "chatRoomId=" + chatRoomId +
                ", userId=" + userId +
                ", userName='" + userName + '\'' +
                ", isTyping=" + isTyping +
                ", timestamp=" + timestamp +
                '}';
    }
}
