package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatResponse
  @version  1.0.0 
  @since 06.10.2026 - 19.33
*/
import com.bricklayers.chatservice.entity.ChatType;

import java.time.LocalDateTime;
import java.util.List;

public record ChatResponse(
        String id,
        ChatType type,
        String name,
        String projectId,
        String brigadeId,
        List<ParticipantResponse> participants,
        String lastMessagePreview,
        LocalDateTime lastMessageAt,
        int unreadCount,
        boolean archived
) {}