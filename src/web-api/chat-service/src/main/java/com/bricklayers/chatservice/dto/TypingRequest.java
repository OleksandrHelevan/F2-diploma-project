package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  TypingRequest
  @version  1.0.0 
  @since 06.10.2026 - 19.32
*/

public record TypingRequest(String threadId, boolean typing) {}