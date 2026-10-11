package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatMessage
  @version  1.0.0 
  @since 06.10.2026 - 19.11
*/

import lombok.*;

import java.time.LocalDateTime;
import java.util.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    private String senderId;
    private String senderName;

    @Builder.Default
    private MessageType type = MessageType.TEXT;

    private String content;
    private LocalDateTime timestamp;

    private LocalDateTime editedAt;
    private boolean deleted;

    private String recipientId;
    private MessageStatus status;

    @Builder.Default
    private Set<String> likedBy = new HashSet<>();

    @Builder.Default
    private List<Attachment> attachments = new ArrayList<>();

    private ThreadInfo thread;
}