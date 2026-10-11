package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  MessagePageResponse
  @version  1.0.0 
  @since 06.10.2026 - 19.33
*/

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> messages,
        boolean hasMore,
        int page
) {}