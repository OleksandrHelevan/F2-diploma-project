package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatServiceException
  @version  1.0.0 
  @since 06.10.2026 - 19.44
*/

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public abstract class ChatServiceException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ChatServiceException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
}