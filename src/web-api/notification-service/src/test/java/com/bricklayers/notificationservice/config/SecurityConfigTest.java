package com.bricklayers.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    @Test
    void serviceAccountTokenWithNotificationServiceAudience_isAccepted() {
        assertThat(SecurityConfig.audienceValidator().validate(jwt(List.of("notification-service", "realm-management")))
                .hasErrors()).isFalse();
    }

    @Test
    void endUserTokenForOtherServices_isRejected() {
        assertThat(SecurityConfig.audienceValidator().validate(jwt(List.of("user-service", "chat-service")))
                .hasErrors()).isTrue();
    }

    @Test
    void userServiceServiceAccount_mayCallTheApi() {
        assertThat(SecurityConfig.isServiceOrAdmin(caller("user-sync-service", List.of()))).isTrue();
    }

    @Test
    void superadminFromSwagger_mayCallTheApi() {
        assertThat(SecurityConfig.isServiceOrAdmin(caller("bricklayers-web-app", List.of("SUPERADMIN")))).isTrue();
    }

    @Test
    void ordinaryUserFromSwagger_mayNotSendEmails() {
        assertThat(SecurityConfig.isServiceOrAdmin(caller("bricklayers-web-app", List.of("BUILDER")))).isFalse();
        assertThat(SecurityConfig.isServiceOrAdmin(Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("azp", "bricklayers-web-app").subject("user").build())).isFalse();
    }

    private static Jwt caller(String clientId, List<String> realmRoles) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .claim("azp", clientId).claim("realm_access", Map.of("roles", realmRoles))
                .subject("subject").build();
    }

    private static Jwt jwt(List<String> audience) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .audience(audience).subject("service-account-user-sync-service")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
