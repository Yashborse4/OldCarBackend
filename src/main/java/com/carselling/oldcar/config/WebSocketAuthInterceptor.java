package com.carselling.oldcar.config;

import com.carselling.oldcar.repository.ChatParticipantRepository;
import com.carselling.oldcar.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Interceptor to enforce authorization on WebSocket subscriptions.
 * Prevents users from subscribing to chat rooms they are not members of.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final ChatParticipantRepository chatParticipantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            Principal principal = accessor.getUser();

            if (destination != null && destination.startsWith("/topic/chat/")) {
                validateChatSubscription(destination, principal);
            }
        }

        return message;
    }

    private void validateChatSubscription(String destination, Principal principal) {
        if (principal == null) {
            log.warn("Unauthenticated subscription attempt to {}", destination);
            throw new AccessDeniedException("User not authenticated");
        }

        UserPrincipal userPrincipal = null;
        if (principal instanceof Authentication) {
            Authentication auth = (Authentication) principal;
            if (auth.getPrincipal() instanceof UserPrincipal) {
                userPrincipal = (UserPrincipal) auth.getPrincipal();
            }
        }

        if (userPrincipal == null) {
            log.warn("Could not extract UserPrincipal from session for subscription to {}", destination);
            throw new AccessDeniedException("Invalid user session");
        }

        // Extract chatRoomId from destination: /topic/chat/{id}/...
        try {
            Long chatRoomId = extractChatRoomId(destination);
            Long userId = userPrincipal.getId();

            boolean isParticipant = chatParticipantRepository
                    .findByChatRoomIdAndUserIdAndIsActiveTrue(chatRoomId, userId)
                    .isPresent();

            if (!isParticipant) {
                log.warn("User {} attempted to subscribe to unauthorized chat room {}", userId, chatRoomId);
                throw new AccessDeniedException("You are not a member of this chat room");
            }

            log.debug("Authorized subscription for user {} to chat {}", userId, chatRoomId);

        } catch (Exception e) {
            log.error("Subscription validation failed: {}", e.getMessage());
            throw new AccessDeniedException("Subscription denied");
        }
    }

    private Long extractChatRoomId(String destination) {
        // Pattern: /topic/chat/{id} or /topic/chat/{id}/...
        String[] parts = destination.split("/");
        // parts[0] = ""
        // parts[1] = "topic"
        // parts[2] = "chat"
        // parts[3] = {id}
        if (parts.length >= 4 && "topic".equals(parts[1]) && "chat".equals(parts[2])) {
            return Long.parseLong(parts[3]);
        }
        throw new IllegalArgumentException("Invalid destination format");
    }
}
