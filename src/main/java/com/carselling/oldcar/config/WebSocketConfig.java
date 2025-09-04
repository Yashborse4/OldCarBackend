package com.carselling.oldcar.config;

import com.carselling.oldcar.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;

/**
 * WebSocket Configuration for real-time chat functionality
 * Configures STOMP protocol, message brokers, and authentication
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * Configure message broker for WebSocket communication
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable a simple in-memory broker for destinations prefixed with "/topic" and "/queue"
        config.enableSimpleBroker("/topic", "/queue", "/user");
        
        // Destination prefixes for client messages
        config.setApplicationDestinationPrefixes("/app");
        
        // User-specific destination prefix
        config.setUserDestinationPrefix("/user");
        
        log.info("WebSocket message broker configured with prefixes: /topic, /queue, /user, /app");
    }

    /**
     * Register STOMP endpoints for WebSocket connections
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the WebSocket endpoint with SockJS fallback
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*") // Configure based on your frontend URL in production
                .withSockJS()
                .setSessionCookieNeeded(false)
                .setHeartbeatTime(25000)
                .setDisconnectDelay(5000);
        
        // Also register without SockJS for native WebSocket clients
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
        
        log.info("WebSocket STOMP endpoints registered at /ws");
    }

    /**
     * Configure client inbound channel for message authentication
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    // Extract JWT token from WebSocket headers
                    String token = accessor.getFirstNativeHeader("Authorization");
                    
                    if (token != null && token.startsWith("Bearer ")) {
                        token = token.substring(7);
                        
                        try {
                            // Validate JWT token
                            if (jwtTokenProvider.validateToken(token)) {
                                // Extract user information
                                String userId = jwtTokenProvider.getUserIdFromToken(token);
                                
                                // Create authentication object
                                Authentication auth = new UsernamePasswordAuthenticationToken(
                                        userId, null, jwtTokenProvider.getAuthoritiesFromToken(token));
                                
                                // Set authentication in security context
                                SecurityContextHolder.getContext().setAuthentication(auth);
                                
                                // Set user principal for WebSocket session
                                accessor.setUser(new Principal() {
                                    @Override
                                    public String getName() {
                                        return userId;
                                    }
                                });
                                
                                log.debug("WebSocket authenticated user: {}", userId);
                            } else {
                                log.warn("Invalid JWT token in WebSocket connection");
                                return null; // Reject the connection
                            }
                        } catch (Exception e) {
                            log.error("Error authenticating WebSocket connection: {}", e.getMessage());
                            return null; // Reject the connection
                        }
                    } else {
                        log.warn("No Authorization header in WebSocket connection");
                        return null; // Reject connections without proper authentication
                    }
                } else if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                    // Handle disconnect
                    Principal user = accessor.getUser();
                    if (user != null) {
                        log.debug("WebSocket user disconnected: {}", user.getName());
                    }
                }
                
                return message;
            }
        });
    }

    /**
     * Configure client outbound channel (optional customization)
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Add outbound interceptors if needed
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                // Log outbound messages for debugging (remove in production)
                log.debug("Outbound WebSocket message: {}", message);
                return message;
            }
        });
    }
}
