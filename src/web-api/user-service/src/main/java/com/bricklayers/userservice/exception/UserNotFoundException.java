package com.bricklayers.userservice.exception;

import lombok.Getter;

@Getter
public class UserNotFoundException extends RuntimeException {

    private final String messageKey;
    private final Object[] args;

    public UserNotFoundException(String messageKey, Object... args) {
        super(messageKey);
        this.messageKey = messageKey;
        this.args = args;
    }

}