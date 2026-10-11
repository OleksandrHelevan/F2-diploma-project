package com.bricklayers.chatservice.service;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatEventPublisher
  @version  1.0.0 
  @since 06.10.2026 - 22.11
*/

import com.bricklayers.chatservice.dto.ChatEventResponse;
import com.bricklayers.chatservice.entity.ChatEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public void publish(ChatEventType type, String chatId, String threadId, String userId, Object payload) {
        ChatEventResponse event = new ChatEventResponse(
                type, chatId, threadId, userId, payload, LocalDateTime.now());

        String destination = threadId == null
                ? "/topic/chats/" + chatId
                : "/topic/chats/" + chatId + "/threads/" + threadId;

        messagingTemplate.convertAndSend(destination, event);
    }
}