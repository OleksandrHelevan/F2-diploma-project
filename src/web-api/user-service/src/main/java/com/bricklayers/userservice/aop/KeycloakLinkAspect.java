package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Operations that must be mirrored to Keycloak (profile update, password reset) cannot run
 * for a local profile without a Keycloak account. An unknown user is left to the method itself,
 * which reports it as 404.
 */
@Aspect
@Component
@Order(KeycloakLinkAspect.ORDER)
@RequiredArgsConstructor
public class KeycloakLinkAspect {

    static final int ORDER = 3;

    private final UserRepository userRepository;

    @Before("@annotation(com.bricklayers.userservice.annotation.RequireKeycloakLink)")
    public void requireKeycloakLink(JoinPoint joinPoint) {
        JoinPointArguments.firstUuid(joinPoint)
                .flatMap(userRepository::findById)
                .filter(user -> user.getKeycloakId() == null || user.getKeycloakId().isBlank())
                .ifPresent(user -> {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "User profile is not linked to a Keycloak account");
                });
    }
}
