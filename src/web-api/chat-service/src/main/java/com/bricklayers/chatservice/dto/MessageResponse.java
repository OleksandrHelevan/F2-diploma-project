package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  MessageResponse
  @version  1.0.0 
  @since 06.10.2026 - 19.32
*/

import com.bricklayers.chatservice.dto.AttachmentDto;
import com.bricklayers.chatservice.entity.MessageStatus;
import com.bricklayers.chatservice.entity.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record MessageResponse(
        String id,
        String senderId,
        String senderName,
        MessageType type,
        String content,
        LocalDateTime timestamp,
        LocalDateTime editedAt,
        boolean deleted,
        String recipientId,
        MessageStatus status,
        int likesCount,
        boolean likedByMe,
        List<AttachmentDto> attachments,
        ThreadInfoResponse thread
) {}