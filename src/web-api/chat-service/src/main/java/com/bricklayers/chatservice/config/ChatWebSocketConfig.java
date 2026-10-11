package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.ChatPrincipal;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class ChatWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final ChatAccessService accessService;

    @Value("${chat.demo.enabled:false}")
    private boolean demoEnabled;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (accessor.getCommand() == StompCommand.CONNECT) {
                    if (!demoEnabled) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chat demo identity headers are disabled");
                    }
                    accessor.setUser(new ChatPrincipal(readUser(accessor)));
                } else if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
                    ChatPrincipal principal = requirePrincipal(accessor);
                    authorizeDestination(accessor.getDestination(), principal.user());
                } else if (accessor.getCommand() == StompCommand.SEND) {
                    requirePrincipal(accessor);
                }
                return message;
            }
        });
    }

    private void authorizeDestination(String destination, UserContext user) {
        if (destination == null || !destination.startsWith("/topic/chats/")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unsupported chat subscription");
        }
        String[] segments = destination.split("/");
        if (segments.length != 4
                && !(segments.length == 6 && "threads".equals(segments[4]))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid chat subscription destination");
        }
        accessService.requireReadAccess(segments[3], user);
    }

    private static ChatPrincipal requirePrincipal(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Connect before using chat");
    }

    private static UserContext readUser(StompHeaderAccessor accessor) {
        try {
            return new UserContext(
                    requiredHeader(accessor, "x-user-id"),
                    decodeName(requiredHeader(accessor, "x-user-name-base64")),
                    requiredHeader(accessor, "x-company-id"),
                    ParticipantRole.valueOf(requiredHeader(accessor, "x-user-role")));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid demo user role", exception);
        }
    }

    private static String requiredHeader(StompHeaderAccessor accessor, String name) {
        String value = accessor.getFirstNativeHeader(name);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing " + name);
        }
        return value;
    }

    private static String decodeName(String encodedName) {
        try {
            return new String(Base64.getDecoder().decode(encodedName), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid x-user-name-base64", exception);
        }
    }
}
