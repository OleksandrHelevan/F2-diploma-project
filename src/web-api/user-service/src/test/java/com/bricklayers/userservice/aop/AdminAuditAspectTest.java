package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.annotation.AuditAdminAction;
import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.AdminAuditLog;
import com.bricklayers.userservice.entity.AuditOutcome;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import com.bricklayers.userservice.service.impl.UserServiceImpl;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class AdminAuditAspectTest {

    @Mock
    private JoinPoint joinPoint;

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    private AdminAuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect = new AdminAuditAspect(auditLogRepository);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void success_recordsActorActionAndTarget(CapturedOutput output) throws NoSuchMethodException {
        UUID targetId = UUID.randomUUID();
        authenticateAs("admin-keycloak-id", "root");
        when(joinPoint.getArgs()).thenReturn(new Object[]{targetId});

        aspect.recordSuccess(joinPoint, auditOf("delete", UUID.class), null);

        assertThat(output).contains("action=USER_DELETE actor=root(admin-keycloak-id) target="
                + targetId + " outcome=SUCCESS");
        AdminAuditLog stored = storedRecord();
        assertThat(stored.getAction()).isEqualTo("USER_DELETE");
        assertThat(stored.getActor()).isEqualTo("root(admin-keycloak-id)");
        assertThat(stored.getTarget()).isEqualTo(targetId.toString());
        assertThat(stored.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(stored.getReason()).isNull();
    }

    @Test
    void createdUser_takesTargetFromReturnedDto(CapturedOutput output) throws NoSuchMethodException {
        UUID createdId = UUID.randomUUID();
        UserDto created = new UserDto(createdId, "builder", "builder@example.com",
                null, null, Role.BUILDER, null, true, null, null);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"request"});

        aspect.recordSuccess(joinPoint, auditOf("create", CreateUserRequest.class), created);

        assertThat(output).contains("action=USER_CREATE actor=system target=" + createdId + " outcome=SUCCESS");
    }

    @Test
    void failure_recordsExceptionType(CapturedOutput output) throws NoSuchMethodException {
        UUID targetId = UUID.randomUUID();
        when(joinPoint.getArgs()).thenReturn(new Object[]{targetId});

        aspect.recordFailure(joinPoint, auditOf("delete", UUID.class),
                new ResponseStatusException(HttpStatus.CONFLICT));

        assertThat(output).contains("action=USER_DELETE actor=system target=" + targetId
                + " outcome=FAILURE reason=ResponseStatusException");
        AdminAuditLog stored = storedRecord();
        assertThat(stored.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(stored.getReason()).isEqualTo("ResponseStatusException");
    }

    @Test
    void storageFailure_doesNotPropagate(CapturedOutput output) throws NoSuchMethodException {
        when(joinPoint.getArgs()).thenReturn(new Object[]{UUID.randomUUID()});
        when(auditLogRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        aspect.recordSuccess(joinPoint, auditOf("delete", UUID.class), null);

        assertThat(output).contains("Failed to store audit record action=USER_DELETE");
    }

    private AdminAuditLog storedRecord() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        return captor.getValue();
    }

    private static AuditAdminAction auditOf(String method, Class<?>... parameterTypes) throws NoSuchMethodException {
        return UserServiceImpl.class.getMethod(method, parameterTypes).getAnnotation(AuditAdminAction.class);
    }

    private static void authenticateAs(String keycloakId, String username) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(keycloakId)
                .claim("preferred_username", username)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
