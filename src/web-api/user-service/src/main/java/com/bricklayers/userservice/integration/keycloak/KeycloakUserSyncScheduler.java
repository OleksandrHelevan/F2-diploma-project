package com.bricklayers.userservice.integration.keycloak;

import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakRole;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakUser;
import com.bricklayers.userservice.integration.keycloak.KeycloakUserImportService.KeycloakUserRecord;
import com.bricklayers.userservice.integration.keycloak.KeycloakUserImportService.SyncResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(name = "keycloak.sync.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KeycloakUserSyncScheduler {

    private static final int PAGE_SIZE = 100;
    private static final Set<Role> KEYCLOAK_ROLES = Set.of(Role.values());
    private static final Set<String> KEYCLOAK_ROLE_NAMES =
            KEYCLOAK_ROLES.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final KeycloakAdminClient keycloakAdminClient;
    private final KeycloakUserImportService importService;

    @Value("${keycloak.sync.default-role:BUILDER}")
    private Role defaultRole;

    @Scheduled(
            initialDelayString = "${keycloak.sync.initial-delay-ms:10000}",
            fixedDelayString = "${keycloak.sync.fixed-delay-ms:60000}"
    )
    public void synchronizeUsers() {
        String accessToken = keycloakAdminClient.getAccessToken();
        List<KeycloakUserRecord> importedUsers = new ArrayList<>();

        for (int first = 0; ; first += PAGE_SIZE) {
            List<KeycloakUser> page = keycloakAdminClient.getUsers(first, PAGE_SIZE, accessToken);
            for (KeycloakUser user : page) {
                if (user.username() != null && user.username().startsWith("service-account-")) {
                    continue;
                }
                if (!hasRequiredIdentity(user)) {
                    log.warn("Skipping Keycloak user without id, username, or email");
                    continue;
                }
                List<KeycloakRole> roles = keycloakAdminClient.getRealmRoles(user.id(), accessToken);
                importedUsers.add(toRecord(user, roles));
            }
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }

        SyncResult result = importService.synchronize(importedUsers);
        log.info("Keycloak user synchronization completed: {} created, {} updated, {} identity conflicts skipped",
                result.created(), result.updated(), result.skippedConflicts());
    }

    private KeycloakUserRecord toRecord(KeycloakUser user, List<KeycloakRole> assignedRoles) {
        Set<Role> mappedRoles = assignedRoles.stream()
                .map(KeycloakRole::name)
                .filter(KEYCLOAK_ROLE_NAMES::contains)
                .map(Role::valueOf)
                .collect(Collectors.toSet());
        if (mappedRoles.size() > 1) {
            throw new IllegalStateException("Keycloak user has multiple application roles: " + user.username());
        }
        Role role = mappedRoles.stream().findFirst().orElse(defaultRole);
        UUID companyId = readCompanyId(user.attributes(), user.username());

        return new KeycloakUserRecord(
                user.id(),
                user.username(),
                user.email(),
                user.firstName(),
                user.lastName(),
                role,
                companyId,
                Boolean.TRUE.equals(user.enabled()));
    }

    private static boolean hasRequiredIdentity(KeycloakUser user) {
        return user.id() != null
                && StringUtils.hasText(user.username())
                && StringUtils.hasText(user.email());
    }

    private static UUID readCompanyId(Map<String, List<String>> attributes, String username) {
        if (attributes == null || attributes.get("companyId") == null
                || attributes.get("companyId").isEmpty()
                || !StringUtils.hasText(attributes.get("companyId").getFirst())) {
            return null;
        }
        try {
            return UUID.fromString(attributes.get("companyId").getFirst());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid companyId attribute for Keycloak user " + username, exception);
        }
    }
}
