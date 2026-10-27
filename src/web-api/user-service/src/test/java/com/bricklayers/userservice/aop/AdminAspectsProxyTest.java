package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import com.bricklayers.userservice.repository.UserRepository;
import com.bricklayers.userservice.service.UserService;
import com.bricklayers.userservice.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Calls the annotated {@link UserServiceImpl} methods through the Spring proxy (as
 * {@code AdminController} does) and through self-invocation, to show where the aspects run.
 */
@SpringJUnitConfig(AdminAspectsProxyTest.AopConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class AdminAspectsProxyTest {

    private static final String ADMIN_KEYCLOAK_ID = "admin-keycloak-id";

    @Configuration
    @EnableAspectJAutoProxy(proxyTargetClass = true)
    @Import({UserServiceImpl.class, AdminAuditAspect.class, SelfTargetGuardAspect.class, KeycloakLinkAspect.class})
    static class AopConfig {
    }

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AdminAuditLogRepository auditLogRepository;

    @MockitoBean
    private UserMapper userMapper;

    @MockitoBean
    private KeycloakAdminClient keycloakAdminClient;

    @Autowired
    private UserService userService;

    private User admin;
    private User otherUser;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(UUID.randomUUID()).username("root")
                .role(Role.SUPERADMIN).keycloakId(ADMIN_KEYCLOAK_ID).build();
        otherUser = User.builder().id(UUID.randomUUID()).username("builder")
                .role(Role.BUILDER).keycloakId("builder-keycloak-id").build();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(userRepository.findById(otherUser.getId())).thenReturn(Optional.of(otherUser));

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(ADMIN_KEYCLOAK_ID)
                .claim("preferred_username", "root")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void injectedServiceIsAnAopProxy() {
        assertThat(AopUtils.isAopProxy(userService)).isTrue();
    }

    @Test
    void externalCall_deleteOwnAccount_isRejectedAndAudited(CapturedOutput output) {
        assertThatThrownBy(() -> userService.delete(admin.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));

        verify(userRepository, never()).delete(any());
        verify(keycloakAdminClient, never()).deleteUser(anyString(), any());
        assertThat(output).contains("action=USER_DELETE actor=root(" + ADMIN_KEYCLOAK_ID + ") target="
                + admin.getId() + " outcome=FAILURE reason=ResponseStatusException");
    }

    @Test
    void externalCall_deleteAnotherUser_proceedsAndIsAudited(CapturedOutput output) {
        userService.delete(otherUser.getId());

        verify(userRepository).delete(otherUser);
        assertThat(output).contains("action=USER_DELETE actor=root(" + ADMIN_KEYCLOAK_ID + ") target="
                + otherUser.getId() + " outcome=SUCCESS");
    }

    /**
     * Self-invocation: {@code deleteAll} calls {@code this.delete(id)}, which never reaches the proxy.
     * The same delete that is rejected above silently succeeds here, and nothing is audited.
     * This is the intended outcome of the lab exercise, not a bug to fix.
     */
    @Test
    void selfInvocation_deleteOwnAccount_bypassesGuardAndAudit(CapturedOutput output) {
        assertThatCode(() -> userService.deleteAll(List.of(admin.getId()))).doesNotThrowAnyException();

        verify(userRepository).delete(admin);
        verify(keycloakAdminClient).deleteUser(ADMIN_KEYCLOAK_ID, null);
        assertThat(output).doesNotContain("action=USER_DELETE");
    }

    @Test
    void externalCall_setPasswordForUnlinkedUser_isRejectedBeforeKeycloakIsCalled(CapturedOutput output) {
        User unlinked = User.builder().id(UUID.randomUUID()).username("legacy").role(Role.BUILDER).build();
        when(userRepository.findById(unlinked.getId())).thenReturn(Optional.of(unlinked));

        assertThatThrownBy(() -> userService.setPassword(unlinked.getId(), new SetPasswordRequest("Secret123!")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not linked to a Keycloak account");

        verify(keycloakAdminClient, never()).getAccessToken();
        verify(keycloakAdminClient, never()).updatePassword(anyString(), anyString(), anyString());
        assertThat(output).contains("action=USER_PASSWORD_RESET");
    }
}
