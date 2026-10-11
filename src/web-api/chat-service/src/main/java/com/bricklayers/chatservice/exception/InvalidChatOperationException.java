package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  InvalidChatOperationException
  @version  1.0.0 
  @since 06.10.2026 - 19.46
*/
import org.springframework.http.HttpStatus;

public class InvalidChatOperationException extends ChatServiceException {
    public InvalidChatOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, "INVALID_OPERATION", message);
    }
}