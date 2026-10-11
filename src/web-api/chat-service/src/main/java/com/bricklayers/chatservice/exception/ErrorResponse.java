package com.bricklayers.chatservice.exception;

/*
  @author   user
  @project   F2-diploma-project
  @class  ErrorResponse
  @version  1.0.0 
  @since 06.10.2026 - 20.00
*/

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ErrorResponse of(HttpStatus status, String code, String message,
                                   String path, Map<String, String> fieldErrors) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), code, message, path, fieldErrors);
    }
}
