package com.bricklayers.userservice.overview;

import java.util.List;

public record KeycloakAccountDto(
        boolean available,
        List<String> realmRoles,
        int activeSessions,
        String loadedBy,
        String error
) {

    public static KeycloakAccountDto loaded(List<String> realmRoles, int activeSessions) {
        return new KeycloakAccountDto(true, realmRoles, activeSessions, Thread.currentThread().getName(), null);
    }

    public static KeycloakAccountDto notLinked() {
        return new KeycloakAccountDto(false, List.of(), 0, null, "User is not linked to a Keycloak account");
    }

    public static KeycloakAccountDto unavailable(Throwable failure) {
        return new KeycloakAccountDto(false, List.of(), 0, null, "Keycloak data unavailable: " + Failures.describe(failure));
    }
}
