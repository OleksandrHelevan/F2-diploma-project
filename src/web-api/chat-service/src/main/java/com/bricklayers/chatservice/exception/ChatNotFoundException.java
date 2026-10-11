package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatNotFoundException
  @version  1.0.0 
  @since 06.10.2026 - 19.45
*/
import org.springframework.http.HttpStatus;

public class ChatNotFoundException extends ChatServiceException {
    public ChatNotFoundException(String chatId) {
        super(HttpStatus.NOT_FOUND, "CHAT_NOT_FOUND", "Чат не знайдено: " + chatId);
    }
}