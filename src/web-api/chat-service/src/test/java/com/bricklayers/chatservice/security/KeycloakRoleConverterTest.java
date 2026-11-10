package com.bricklayers.chatservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    @Test
    void mapsRealmRolesToPrefixedAuthorities() {
        Jwt jwt = jwt().claim("realm_access", Map.of("roles", List.of("FOREMAN", 5, "offline_access"))).build();

        assertThat(KeycloakRoleConverter.fromJwt(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FOREMAN", "ROLE_offline_access");
    }

    @Test
    void returnsEmptyWhenRealmAccessMissingOrMalformed() {
        assertThat(KeycloakRoleConverter.fromJwt(jwt().claim("sub", "x").build())).isEmpty();
        assertThat(KeycloakRoleConverter.fromJwt(jwt().claim("realm_access", "oops").build())).isEmpty();
        assertThat(KeycloakRoleConverter.fromJwt(jwt().claim("realm_access", Map.of("roles", "FOREMAN")).build())).isEmpty();
    }

    private static Jwt.Builder jwt() {
        return Jwt.withTokenValue("t").header("alg", "none");
    }
}
