package com.bricklayers.userservice.aop;

import org.aspectj.lang.JoinPoint;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

final class JoinPointArguments {

    private JoinPointArguments() {
    }

    static Optional<UUID> firstUuid(JoinPoint joinPoint) {
        return Arrays.stream(joinPoint.getArgs())
                .filter(UUID.class::isInstance)
                .map(UUID.class::cast)
                .findFirst();
    }
}
