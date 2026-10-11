package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  UserNotFoundException
  @version  1.0.0 
  @since 06.10.2026 - 19.45
*/
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ChatServiceException {
    public UserNotFoundException(String userId) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Користувача не знайдено: " + userId);
    }
}
