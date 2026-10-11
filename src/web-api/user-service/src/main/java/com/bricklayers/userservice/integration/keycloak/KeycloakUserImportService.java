package com.bricklayers.userservice.integration.keycloak;

import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "keycloak.sync.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KeycloakUserImportService {

    private final UserRepository userRepository;

    @Transactional
    public SyncResult synchronize(List<KeycloakUserRecord> remoteUsers) {
        int created = 0;
        int updated = 0;

        for (KeycloakUserRecord remote : remoteUsers) {
            User user = findExisting(remote);
            if (user == null) {
                user = new User();
                user.setKeycloakId(remote.keycloakId());
                created++;
            } else {
                updated++;
                user.setKeycloakId(remote.keycloakId());
            }

            user.setUsername(remote.username());
            user.setEmail(remote.email());
            user.setFirstName(remote.firstName());
            user.setLastName(remote.lastName());
            user.setRole(remote.role());
            user.setActive(remote.active());
            if (remote.companyId() != null) {
                user.setCompanyId(remote.companyId());
            }
            userRepository.save(user);
        }

        return new SyncResult(created, updated);
    }

    private User findExisting(KeycloakUserRecord remote) {
        List<User> byKeycloakId = userRepository.findAllByKeycloakId(remote.keycloakId());
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
        if (byKeycloakId.size() > 1) {
            throw new IllegalStateException(
                    "Multiple local accounts have the Keycloak ID for user: " + remote.username());
        }
        return byKeycloakId.stream().findFirst().orElse(null);
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

    public record SyncResult(int created, int updated) {}
}
