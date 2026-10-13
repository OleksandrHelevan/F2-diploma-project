package com.bricklayers.chatservice.security;

import com.bricklayers.chatservice.client.UserDirectory;
import com.bricklayers.chatservice.client.UserInfo;
import com.bricklayers.chatservice.entity.ParticipantRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatIdentityServiceTest {

    @Mock
    private UserDirectory userDirectory;

    @InjectMocks
    private ChatIdentityService identityService;

    @Test
    void resolvesKeycloakSubjectToActiveLocalProfileAndTokenRole() {
        Jwt jwt = keycloakJwt(List.of("BUILDER", "offline_access"));
        when(userDirectory.getByKeycloakId("keycloak-id", "Bearer access-token"))
                .thenReturn(new UserInfo(
                        "local-user-id", "builder", "Ivan", "Builder",
                        "company-id", "FOREMAN", true));

        UserContext context = identityService.resolve(jwt);

        assertThat(context).isEqualTo(new UserContext(
                "local-user-id", "Ivan Builder", "company-id", ParticipantRole.BUILDER));
        verify(userDirectory).getByKeycloakId("keycloak-id", "Bearer access-token");
    }

    @Test
    void rejectsTokensWithMultipleApplicationRoles() {
        Jwt jwt = keycloakJwt(List.of("BUILDER", "FOREMAN"));
        when(userDirectory.getByKeycloakId("keycloak-id", "Bearer access-token"))
                .thenReturn(new UserInfo(
                        "local-user-id", "builder", "Ivan", "Builder",
                        "company-id", "BUILDER", true));

        assertThatThrownBy(() -> identityService.resolve(jwt))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Keycloak token must have one chat role");
    }

    private static Jwt keycloakJwt(List<String> roles) {
        return Jwt.withTokenValue("access-token")
                .header("alg", "RS256")
                .subject("keycloak-id")
                .claim("realm_access", Map.of("roles", roles))
                .build();
    }
}
