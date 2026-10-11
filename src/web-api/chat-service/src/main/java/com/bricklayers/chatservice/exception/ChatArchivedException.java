package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatArchivedException
  @version  1.0.0 
  @since 06.10.2026 - 20.00
*/
import org.springframework.http.HttpStatus;

public class ChatArchivedException extends ChatServiceException {
    public ChatArchivedException(String chatId) {
        super(HttpStatus.CONFLICT, "CHAT_ARCHIVED", "Чат архівований, писати в нього не можна: " + chatId);
    }
}