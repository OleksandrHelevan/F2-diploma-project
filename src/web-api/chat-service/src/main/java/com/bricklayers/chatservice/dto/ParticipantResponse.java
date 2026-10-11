package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  ParticipantResponse
  @version  1.0.0 
  @since 06.10.2026 - 19.33
*/

import com.bricklayers.chatservice.entity.ParticipantRole;

import java.time.LocalDateTime;

public record ParticipantResponse(
        String userId,
        ParticipantRole role,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        boolean muted
) {}