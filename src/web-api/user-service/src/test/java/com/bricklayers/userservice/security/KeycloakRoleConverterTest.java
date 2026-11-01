package com.bricklayers.userservice.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    @Test
    void fromJwt_realmRoles_areMappedToSpringRoles() {
        Jwt jwt = jwt(Map.of("roles", List.of("SUPERADMIN", "offline_access")));

        assertThat(authorities(KeycloakRoleConverter.fromJwt(jwt)))
                .containsExactly("ROLE_SUPERADMIN", "ROLE_offline_access");
    }

    @Test
    void fromJwt_withoutRealmAccess_returnsNoRoles() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user").build();

        assertThat(KeycloakRoleConverter.fromJwt(jwt)).isEmpty();
    }

    @Test
    void fromJwt_rolesNotACollection_returnsNoRoles() {
        assertThat(KeycloakRoleConverter.fromJwt(jwt(Map.of("roles", "SUPERADMIN")))).isEmpty();
    }

    @Test
    void fromJwt_nonStringRoles_areIgnored() {
        Jwt jwt = jwt(Map.of("roles", List.of("BUILDER", 42)));

        assertThat(authorities(KeycloakRoleConverter.fromJwt(jwt))).containsExactly("ROLE_BUILDER");
    }

    @Test
    void fromOidcUserAuthority_rolesInIdToken_areUsed() {
        OidcIdToken idToken = idToken(Map.of("roles", List.of("FOREMAN")));
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "user",
                "realm_access", Map.of("roles", List.of("CUSTOMER"))));

        Collection<GrantedAuthority> roles = KeycloakRoleConverter.fromOidcUserAuthority(
                new OidcUserAuthority(idToken, userInfo));

        assertThat(authorities(roles)).containsExactly("ROLE_FOREMAN");
    }

    @Test
    void fromOidcUserAuthority_noRolesInIdToken_fallsBackToUserInfo() {
        OidcIdToken idToken = OidcIdToken.withTokenValue("id-token").subject("user").build();
        OidcUserInfo userInfo = new OidcUserInfo(Map.of("sub", "user",
                "realm_access", Map.of("roles", List.of("SUPPLIER"))));

        Collection<GrantedAuthority> roles = KeycloakRoleConverter.fromOidcUserAuthority(
                new OidcUserAuthority(idToken, userInfo));

        assertThat(authorities(roles)).containsExactly("ROLE_SUPPLIER");
    }

    private static Jwt jwt(Map<String, Object> realmAccess) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("user")
                .claim("realm_access", realmAccess)
                .build();
    }

    private static OidcIdToken idToken(Map<String, Object> realmAccess) {
        return OidcIdToken.withTokenValue("id-token")
                .subject("user")
                .claim("realm_access", realmAccess)
                .build();
    }

    private static List<String> authorities(Collection<GrantedAuthority> roles) {
        return roles.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
