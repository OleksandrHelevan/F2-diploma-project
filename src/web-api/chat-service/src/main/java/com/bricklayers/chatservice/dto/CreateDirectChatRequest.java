package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  CreateDirectChatRequest
  @version  1.0.0 
  @since 06.10.2026 - 19.31
*/

import com.bricklayers.chatservice.entity.ChatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateDirectChatRequest(
        @NotNull ChatType type,
        @NotBlank String targetUserId
) {}
