package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  ThreadInfoResponse
  @version  1.0.0 
  @since 06.10.2026 - 19.32
*/
import java.time.LocalDateTime;
import java.util.Set;

public record ThreadInfoResponse(
        int replyCount,
        LocalDateTime lastReplyAt,
        Set<String> participantIds
) {}
