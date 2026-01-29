package com.carselling.oldcar.service;

public interface RateLimitingService {
    /**
     * Try to consume a token for sending a message in a specific chat room.
     * 
     * @param userId     The ID of the user sending the message
     * @param chatRoomId The ID of the chat room
     * @return true if the token was consumed and action is allowed, false if limit
     *         exceeded
     */
    boolean tryConsumeMessageLimit(Long userId, Long chatRoomId);
}
