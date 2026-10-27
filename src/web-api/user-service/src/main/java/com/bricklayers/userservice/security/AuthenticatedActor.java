package com.bricklayers.userservice.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public final class AuthenticatedActor {

    private AuthenticatedActor() {
    }

    public static Optional<Jwt> jwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(jwtAuthentication.getToken());
        }
        return Optional.empty();
    }

    public static Optional<String> keycloakId() {
        return jwt().map(Jwt::getSubject);
    }

    public static String describe() {
        return jwt()
                .map(token -> {
                    String username = token.getClaimAsString("preferred_username");
                    return username == null ? token.getSubject() : username + "(" + token.getSubject() + ")";
                })
                .orElse("system");
    }
}
