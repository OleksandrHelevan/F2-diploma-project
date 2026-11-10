package com.bricklayers.userservice.integration.keycloak;

import com.bricklayers.userservice.cache.UserProfileCache;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "keycloak.sync.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class KeycloakUserImportService {

    private final UserRepository userRepository;
    private final UserProfileCache profileCache;

    @Transactional
    public SyncResult synchronize(List<KeycloakUserRecord> remoteUsers) {
        int created = 0;
        int updated = 0;
        int skippedConflicts = 0;

        for (KeycloakUserRecord remote : remoteUsers) {
            User user;
            try {
                user = findExisting(remote);
            } catch (IllegalStateException exception) {
                skippedConflicts++;
                log.warn("Skipping Keycloak user synchronization due to an identity conflict "
                        + "(keycloakId={}): {}", remote.keycloakId(), exception.getMessage());
                continue;
            }
            boolean existing = user != null;
            if (!existing) {
                user = new User();
                created++;
            } else if (matches(user, remote)) {

                continue;
            } else {
                updated++;
            }

            user.setKeycloakId(remote.keycloakId());

            user.setUsername(remote.username());
            user.setEmail(remote.email());
            user.setFirstName(remote.firstName());
            user.setLastName(remote.lastName());
            user.setRole(remote.role());
            user.setActive(remote.active());
            user.setCompanyId(remote.companyId());
            userRepository.save(user);
            if (existing) {
                profileCache.evict(user.getId(), remote.keycloakId());
            }
        }

        return new SyncResult(created, updated, skippedConflicts);
    }

    private static boolean matches(User user, KeycloakUserRecord remote) {
        return Objects.equals(user.getKeycloakId(), remote.keycloakId())
                && Objects.equals(user.getUsername(), remote.username())
                && Objects.equals(user.getEmail(), remote.email())
                && Objects.equals(user.getFirstName(), remote.firstName())
                && Objects.equals(user.getLastName(), remote.lastName())
                && user.getRole() == remote.role()
                && user.isActive() == remote.active()
                && Objects.equals(user.getCompanyId(), remote.companyId());
    }

    private User findExisting(KeycloakUserRecord remote) {
        Optional<User> byKeycloakId = userRepository.findByKeycloakId(remote.keycloakId());
        Optional<User> byUsername = userRepository.findByUsername(remote.username());
        Optional<User> byEmail = userRepository.findByEmail(remote.email());

        if (byUsername.isPresent() && byEmail.isPresent()
                && !byUsername.get().getId().equals(byEmail.get().getId())) {
            throw new IllegalStateException(
                    "Keycloak user identity conflicts with multiple local accounts: " + remote.username());
        }

        Optional<User> matchingProfile = byUsername.or(() -> byEmail);
        if (matchingProfile.isPresent()) {
            User user = matchingProfile.get();
            if (user.getKeycloakId() != null && !user.getKeycloakId().equals(remote.keycloakId())) {
                throw new IllegalStateException(
                        "Local account is linked to a different Keycloak user: " + remote.username());
            }
            return user;
        }
        return byKeycloakId.orElse(null);
    }

    public record KeycloakUserRecord(
            String keycloakId,
            String username,
            String email,
            String firstName,
            String lastName,
            Role role,
            UUID companyId,
            boolean active
    ) {}

    public record SyncResult(int created, int updated, int skippedConflicts) {}
}
