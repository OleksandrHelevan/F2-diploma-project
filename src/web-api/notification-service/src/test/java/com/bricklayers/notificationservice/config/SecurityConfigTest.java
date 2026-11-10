package com.bricklayers.notificationservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

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

    private static Jwt jwt(List<String> audience) {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .audience(audience).subject("service-account-user-sync-service")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
