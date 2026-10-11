package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  WebSocketExceptionHandler
  @version  1.0.0 
  @since 06.10.2026 - 20.01
*/

import com.bricklayers.chatservice.dto.ChatEventResponse;
import com.bricklayers.chatservice.entity.ChatEventType;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.time.LocalDateTime;

@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(ChatServiceException.class)
    @SendToUser("/queue/errors")
    public ChatEventResponse handle(ChatServiceException ex) {
        return new ChatEventResponse(ChatEventType.ERROR, null, null, null, ex.getMessage(), LocalDateTime.now());
    }
}
