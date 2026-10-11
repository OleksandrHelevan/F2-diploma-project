package com.bricklayers.chatservice.controller;

import com.bricklayers.chatservice.dto.TypingRequest;
import com.bricklayers.chatservice.security.ChatPrincipal;
import com.bricklayers.chatservice.service.TypingService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final TypingService typingService;

    @MessageMapping("/chats/{chatId}/typing")
    public void typing(
            @DestinationVariable String chatId,
            @Payload TypingRequest request,
            Principal principal
    ) {
        if (!(principal instanceof ChatPrincipal chatPrincipal)) {
            throw new IllegalArgumentException("Chat connection has no user identity");
        }
        typingService.notifyTyping(chatId, request, chatPrincipal.user());
    }
}
