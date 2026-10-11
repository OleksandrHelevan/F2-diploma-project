package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  MessageNotFoundException
  @version  1.0.0 
  @since 06.10.2026 - 19.45
*/

import org.springframework.http.HttpStatus;

public class MessageNotFoundException extends ChatServiceException {
    public MessageNotFoundException(String messageId) {
        super(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Повідомлення не знайдено: " + messageId);
    }
}
