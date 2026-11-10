package com.bricklayers.userservice.integration.keycloak;

import com.bricklayers.userservice.cache.UserProfileCache;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.integration.keycloak.KeycloakUserImportService.KeycloakUserRecord;
import com.bricklayers.userservice.integration.keycloak.KeycloakUserImportService.SyncResult;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserImportServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserProfileCache profileCache;

    @InjectMocks
    private KeycloakUserImportService importService;

    @Test
    void synchronize_importsNewUserAndKeepsDisabledState() {
        KeycloakUserRecord remote = new KeycloakUserRecord(
                "keycloak-id", "builder", "builder@example.com",
                "Ivan", "Builder", Role.BUILDER, null, false);
        when(userRepository.findByKeycloakId(remote.keycloakId())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(remote.username())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(remote.email())).thenReturn(Optional.empty());

        SyncResult result = importService.synchronize(List.of(remote));

        assertThat(result).isEqualTo(new SyncResult(1, 0, 0));
        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User imported = userCaptor.getValue();
        assertThat(imported.getKeycloakId()).isEqualTo(remote.keycloakId());
        assertThat(imported.getRole()).isEqualTo(Role.BUILDER);
        assertThat(imported.isActive()).isFalse();
    }

    @Test
    void synchronizeSkipsConflictingIdentityAndContinuesImportingOtherUsers() {
        KeycloakUserRecord conflicting = new KeycloakUserRecord(
                "new-keycloak-id", "builder", "builder@example.com",
                "Ivan", "Builder", Role.BUILDER, null, true);
        KeycloakUserRecord valid = new KeycloakUserRecord(
                "another-keycloak-id", "foreman", "foreman@example.com",
                "Olena", "Foreman", Role.FOREMAN, null, true);
        User existing = User.builder()
                .id(java.util.UUID.randomUUID())
                .username("builder")
                .email("builder@example.com")
                .keycloakId("old-keycloak-id")
                .role(Role.BUILDER)
                .active(true)
                .build();
        when(userRepository.findByKeycloakId(conflicting.keycloakId())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(conflicting.username())).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail(conflicting.email())).thenReturn(Optional.of(existing));
        when(userRepository.findByKeycloakId(valid.keycloakId())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(valid.username())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(valid.email())).thenReturn(Optional.empty());

        SyncResult result = importService.synchronize(List.of(conflicting, valid));

        assertThat(result).isEqualTo(new SyncResult(1, 0, 1));
        assertThat(existing.getKeycloakId()).isEqualTo("old-keycloak-id");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                user -> valid.keycloakId().equals(user.getKeycloakId())));
    }

    @Test
    void synchronizeStillFailsForUnexpectedPersistenceErrors() {
        KeycloakUserRecord remote = new KeycloakUserRecord(
                "keycloak-id", "builder", "builder@example.com",
                "Ivan", "Builder", Role.BUILDER, null, true);
        when(userRepository.findByKeycloakId(remote.keycloakId())).thenReturn(Optional.empty());
        when(userRepository.findByUsername(remote.username())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(remote.email())).thenReturn(Optional.empty());
        when(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .thenThrow(new IllegalStateException("Database unavailable"));

        assertThatThrownBy(() -> importService.synchronize(List.of(remote)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Database unavailable");
    }

    @Test
    void synchronize_unchangedProfile_isNotRewrittenOrEvicted() {
        KeycloakUserRecord remote = new KeycloakUserRecord(
                "keycloak-id", "builder", "builder@example.com",
                "Ivan", "Builder", Role.BUILDER, null, true);
        User existing = User.builder()
                .id(java.util.UUID.randomUUID())
                .keycloakId("keycloak-id").username("builder").email("builder@example.com")
                .firstName("Ivan").lastName("Builder").role(Role.BUILDER).active(true)
                .build();
        when(userRepository.findByKeycloakId(remote.keycloakId())).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername(remote.username())).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail(remote.email())).thenReturn(Optional.of(existing));

        assertThat(importService.synchronize(List.of(remote))).isEqualTo(new SyncResult(0, 0, 0));
        verify(userRepository, never()).save(any(User.class));
        verify(profileCache, never()).evict(any(), any());
    }

    @Test
    void synchronize_changedProfile_isSavedAndEvictedFromCache() {
        KeycloakUserRecord remote = new KeycloakUserRecord(
                "keycloak-id", "builder", "builder@example.com",
                "Ivan", "Renamed", Role.BUILDER, null, true);
        User existing = User.builder()
                .id(java.util.UUID.randomUUID())
                .keycloakId("keycloak-id").username("builder").email("builder@example.com")
                .firstName("Ivan").lastName("Builder").role(Role.BUILDER).active(true)
                .build();
        when(userRepository.findByKeycloakId(remote.keycloakId())).thenReturn(Optional.of(existing));
        when(userRepository.findByUsername(remote.username())).thenReturn(Optional.of(existing));
        when(userRepository.findByEmail(remote.email())).thenReturn(Optional.of(existing));

        assertThat(importService.synchronize(List.of(remote))).isEqualTo(new SyncResult(0, 1, 0));
        assertThat(existing.getLastName()).isEqualTo("Renamed");
        verify(userRepository).save(existing);
        verify(profileCache).evict(existing.getId(), "keycloak-id");
    }
}
