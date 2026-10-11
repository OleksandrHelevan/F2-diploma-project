package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatEventResponse
  @version  1.0.0 
  @since 06.10.2026 - 19.33
*/
import com.bricklayers.chatservice.entity.ChatEventType;

import java.time.LocalDateTime;


public record ChatEventResponse(
        ChatEventType type,
        String chatId,
        String threadId,
        String userId,
        Object payload,
        LocalDateTime at
) {}
