package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatAccessDeniedException
  @version  1.0.0 
  @since 06.10.2026 - 19.46
*/

import org.springframework.http.HttpStatus;

public class ChatAccessDeniedException extends ChatServiceException {
    public ChatAccessDeniedException(String message) {
        super(HttpStatus.FORBIDDEN, "ACCESS_DENIED", message);
    }
}