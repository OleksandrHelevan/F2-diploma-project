package com.bricklayers.userservice.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class KeycloakRoleConverter {

    private KeycloakRoleConverter() {
    }

    public static Collection<GrantedAuthority> fromJwt(Jwt jwt) {
        return fromRealmAccessClaim(jwt.getClaim("realm_access"));
    }

    public static Collection<GrantedAuthority> fromOidcUserAuthority(OidcUserAuthority authority) {
        Set<GrantedAuthority> roles = new LinkedHashSet<>(fromRealmAccessClaim(
                authority.getIdToken().getClaim("realm_access")));
        if (roles.isEmpty()) {
            roles.addAll(fromRealmAccessClaim(authority.getUserInfo().getClaim("realm_access")));
        }
        return roles;
    }

    private static Collection<GrantedAuthority> fromRealmAccessClaim(Object realmAccess) {
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return List.of();
        }
        Object rolesObj = map.get("roles");
        if (!(rolesObj instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
