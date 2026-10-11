package com.bricklayers.userservice.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAuthConfigTest {

    private final LocalAuthConfig config = new LocalAuthConfig();

    @Test
    void locallySignedTokenCanBeValidatedByResourceServerDecoder() {
        var secret = config.localJwtSecret("local-test-secret-with-at-least-32-characters");
        JwtEncoder encoder = config.jwtEncoder(secret);
        JwtDecoder decoder = new SecurityConfig().jwtDecoder(secret, "bricklayers-user-service");
        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("bricklayers-user-service")
                .subject("d7760321-b3f8-49ea-bd00-3e4bf4813747")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        assertThat(decoder.decode(token).getSubject())
                .isEqualTo("d7760321-b3f8-49ea-bd00-3e4bf4813747");
    }

    @Test
    void rejectsJwtSecretsShorterThan32Bytes() {
        assertThatThrownBy(() -> config.localJwtSecret("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 UTF-8 bytes");
    }
}
