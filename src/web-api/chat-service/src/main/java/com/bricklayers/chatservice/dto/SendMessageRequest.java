package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  SendMessageRequest
  @version  1.0.0 
  @since 06.10.2026 - 19.31
*/

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SendMessageRequest(
        @Size(max = 4000) String content,
        String threadId,
        String recipientId,
        @Valid @Size(max = 10) List<AttachmentDto> attachments
) {}
