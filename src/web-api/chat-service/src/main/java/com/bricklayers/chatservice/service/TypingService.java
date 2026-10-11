package com.bricklayers.chatservice.service;

/*
  @author   user
  @project   F2-diploma-project
  @class  TypingService
  @version  1.0.0 
  @since 06.10.2026 - 22.13
*/

import com.bricklayers.chatservice.dto.TypingRequest;
import com.bricklayers.chatservice.entity.ChatEventType;
import com.bricklayers.chatservice.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TypingService {

    private final ChatAccessService accessService;
    private final ChatEventPublisher eventPublisher;

    public void notifyTyping(String chatId, TypingRequest request, UserContext user) {
        accessService.requireWriteAccess(chatId, user);

        String threadId = request.threadId() == null || request.threadId().isBlank() ? null : request.threadId();
        ChatEventType type = request.typing() ? ChatEventType.TYPING : ChatEventType.STOPPED_TYPING;

        eventPublisher.publish(type, chatId, threadId, user.userId(), user.name());
    }
}
