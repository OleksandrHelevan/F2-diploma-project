package com.bricklayers.userservice.integration.keycloak;

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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserImportServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private KeycloakUserImportService importService;

    @Test
    void synchronize_importsNewUserAndKeepsDisabledState() {
        KeycloakUserRecord remote = new KeycloakUserRecord(
                "keycloak-id", "builder", "builder@example.com",
                "Ivan", "Builder", Role.BUILDER, null, false);
        when(userRepository.findAllByKeycloakId(remote.keycloakId())).thenReturn(List.of());
        when(userRepository.findByUsername(remote.username())).thenReturn(Optional.empty());
        when(userRepository.findByEmail(remote.email())).thenReturn(Optional.empty());

        SyncResult result = importService.synchronize(List.of(remote));

        assertThat(result).isEqualTo(new SyncResult(1, 0));
        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User imported = userCaptor.getValue();
        assertThat(imported.getKeycloakId()).isEqualTo(remote.keycloakId());
        assertThat(imported.getRole()).isEqualTo(Role.BUILDER);
        assertThat(imported.isActive()).isFalse();
    }
}
