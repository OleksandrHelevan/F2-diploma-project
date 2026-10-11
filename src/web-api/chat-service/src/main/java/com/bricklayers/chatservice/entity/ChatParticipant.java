package com.bricklayers.chatservice.entity;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatParticipant
  @version  1.0.0 
  @since 06.10.2026 - 19.22
*/

import lombok.*;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatParticipant {
    private String userId;
    private ParticipantRole role;

    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;

    private LocalDateTime lastReadAt;
    private boolean muted;
}