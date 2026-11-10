package com.bricklayers.userservice.integration.keycloak;

import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakRole;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakUser;
import com.bricklayers.userservice.integration.keycloak.KeycloakUserImportService.KeycloakUserRecord;
import com.bricklayers.userservice.integration.keycloak.KeycloakUserImportService.SyncResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakUserSyncSchedulerTest {

    private static final String TOKEN = "admin-token";

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @Mock
    private KeycloakUserImportService importService;

    @InjectMocks
    private KeycloakUserSyncScheduler scheduler;

    @Captor
    private ArgumentCaptor<List<KeycloakUserRecord>> recordsCaptor;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "defaultRole", Role.BUILDER);
        when(keycloakAdminClient.getAccessToken()).thenReturn(TOKEN);
    }

    @Test
    void synchronizeUsers_mapsRolesAndAttributesAndSkipsTechnicalUsers() {
        UUID companyId = UUID.randomUUID();
        KeycloakUser admin = user("kc-admin", "admin", "admin@example.com", Map.of());
        KeycloakUser supplier = user("kc-supplier", "supplier", "supplier@example.com",
                Map.of("companyId", List.of(companyId.toString())));
        KeycloakUser serviceAccount = user("kc-sa", "service-account-user-sync-service", "sa@example.com", Map.of());
        KeycloakUser withoutEmail = user("kc-no-email", "no-email", null, Map.of());

        when(keycloakAdminClient.getUsers(0, 100, TOKEN))
                .thenReturn(List.of(admin, supplier, serviceAccount, withoutEmail));
        when(keycloakAdminClient.getRealmRoles("kc-admin", TOKEN))
                .thenReturn(List.of(new KeycloakRole("1", "SUPERADMIN"), new KeycloakRole("2", "offline_access")));
        when(keycloakAdminClient.getRealmRoles("kc-supplier", TOKEN))
                .thenReturn(List.of(new KeycloakRole("3", "SUPPLIER")));
        when(importService.synchronize(any())).thenReturn(new SyncResult(1, 1, 0));

        scheduler.synchronizeUsers();

        verify(importService).synchronize(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue()).containsExactly(
                new KeycloakUserRecord("kc-admin", "admin", "admin@example.com", "First", "Last",
                        Role.SUPERADMIN, null, true),
                new KeycloakUserRecord("kc-supplier", "supplier", "supplier@example.com", "First", "Last",
                        Role.SUPPLIER, companyId, true));
        verify(keycloakAdminClient, never()).getRealmRoles(eq("kc-sa"), anyString());
        verify(keycloakAdminClient, never()).getRealmRoles(eq("kc-no-email"), anyString());
    }

    @Test
    void synchronizeUsers_userWithoutApplicationRole_getsDefaultRole() {
        KeycloakUser plain = user("kc-plain", "plain", "plain@example.com", null);
        when(keycloakAdminClient.getUsers(0, 100, TOKEN)).thenReturn(List.of(plain));
        when(keycloakAdminClient.getRealmRoles("kc-plain", TOKEN))
                .thenReturn(List.of(new KeycloakRole("1", "default-roles-bricklayers")));
        when(importService.synchronize(any())).thenReturn(new SyncResult(1, 0, 0));

        scheduler.synchronizeUsers();

        verify(importService).synchronize(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue()).singleElement()
                .satisfies(record -> {
                    assertThat(record.role()).isEqualTo(Role.BUILDER);
                    assertThat(record.companyId()).isNull();
                });
    }

    @Test
    void synchronizeUsers_fullPage_requestsNextPage() {
        List<KeycloakUser> firstPage = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            firstPage.add(user("kc-" + i, "user" + i, "user" + i + "@example.com", Map.of()));
        }
        KeycloakUser last = user("kc-last", "last", "last@example.com", Map.of());
        when(keycloakAdminClient.getUsers(0, 100, TOKEN)).thenReturn(firstPage);
        when(keycloakAdminClient.getUsers(100, 100, TOKEN)).thenReturn(List.of(last));
        when(keycloakAdminClient.getRealmRoles(anyString(), eq(TOKEN))).thenReturn(List.of());
        when(importService.synchronize(any())).thenReturn(new SyncResult(101, 0, 0));

        scheduler.synchronizeUsers();

        verify(importService).synchronize(recordsCaptor.capture());
        assertThat(recordsCaptor.getValue()).hasSize(101);
    }

    @Test
    void synchronizeUsers_multipleApplicationRoles_failsWithoutImporting() {
        KeycloakUser conflicted = user("kc-both", "both", "both@example.com", Map.of());
        when(keycloakAdminClient.getUsers(0, 100, TOKEN)).thenReturn(List.of(conflicted));
        when(keycloakAdminClient.getRealmRoles("kc-both", TOKEN))
                .thenReturn(List.of(new KeycloakRole("1", "SUPERADMIN"), new KeycloakRole("2", "BUILDER")));

        assertThatThrownBy(() -> scheduler.synchronizeUsers())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("multiple application roles");
        verify(importService, never()).synchronize(any());
    }

    @Test
    void synchronizeUsers_invalidCompanyId_failsWithoutImporting() {
        KeycloakUser broken = user("kc-broken", "broken", "broken@example.com",
                Map.of("companyId", List.of("not-a-uuid")));
        when(keycloakAdminClient.getUsers(0, 100, TOKEN)).thenReturn(List.of(broken));
        when(keycloakAdminClient.getRealmRoles("kc-broken", TOKEN)).thenReturn(List.of());

        assertThatThrownBy(() -> scheduler.synchronizeUsers())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid companyId");
        verify(importService, never()).synchronize(any());
    }

    @Test
    void synchronizeUsers_keycloakNotReachable_skipsThisRunWithoutThrowing() {
        when(keycloakAdminClient.getAccessToken()).thenThrow(new ResourceAccessException(
                "I/O error on POST request", new java.net.ConnectException("Connection refused")));

        assertThatCode(() -> scheduler.synchronizeUsers()).doesNotThrowAnyException();
        verify(importService, never()).synchronize(any());
    }

    private static KeycloakUser user(String id, String username, String email, Map<String, List<String>> attributes) {
        return new KeycloakUser(id, username, email, "First", "Last", true, attributes);
    }
}
