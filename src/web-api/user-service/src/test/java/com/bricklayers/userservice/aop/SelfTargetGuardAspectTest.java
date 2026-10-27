package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.annotation.ForbidSelfTarget;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import com.bricklayers.userservice.service.impl.UserServiceImpl;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfTargetGuardAspectTest {

    private static final String ADMIN_KEYCLOAK_ID = "admin-keycloak-id";

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @InjectMocks
    private SelfTargetGuardAspect aspect;

    private ForbidSelfTarget guard;
    private UUID targetId;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        guard = UserServiceImpl.class.getMethod("delete", UUID.class).getAnnotation(ForbidSelfTarget.class);
        targetId = UUID.randomUUID();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownAccount_rejectsWithConflictAndNeverProceeds() throws Throwable {
        authenticateAs(ADMIN_KEYCLOAK_ID);
        when(joinPoint.getArgs()).thenReturn(new Object[]{targetId});
        when(userRepository.findById(targetId)).thenReturn(Optional.of(userWithKeycloakId(ADMIN_KEYCLOAK_ID)));

        assertThatThrownBy(() -> aspect.rejectSelfTarget(joinPoint, guard))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException status = (ResponseStatusException) ex;
                    assertThat(status.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(status.getReason()).isEqualTo("Administrators cannot delete their own account");
                });
        verify(joinPoint, never()).proceed();
    }

    @Test
    void anotherUsersAccount_proceedsAndReturnsMethodResult() throws Throwable {
        authenticateAs(ADMIN_KEYCLOAK_ID);
        when(joinPoint.getArgs()).thenReturn(new Object[]{targetId});
        when(userRepository.findById(targetId)).thenReturn(Optional.of(userWithKeycloakId("other-keycloak-id")));
        when(joinPoint.proceed()).thenReturn("deleted");

        Object result = aspect.rejectSelfTarget(joinPoint, guard);

        assertThat(result).isEqualTo("deleted");
        verify(joinPoint).proceed();
    }

    @Test
    void unknownTarget_proceedsSoMethodCanReportNotFound() throws Throwable {
        authenticateAs(ADMIN_KEYCLOAK_ID);
        when(joinPoint.getArgs()).thenReturn(new Object[]{targetId});
        when(userRepository.findById(targetId)).thenReturn(Optional.empty());

        aspect.rejectSelfTarget(joinPoint, guard);

        verify(joinPoint).proceed();
    }

    @Test
    void noAuthenticatedUser_proceedsWithoutLookingUpTarget() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{targetId});

        aspect.rejectSelfTarget(joinPoint, guard);

        verify(joinPoint).proceed();
        verifyNoInteractions(userRepository);
    }

    private static void authenticateAs(String keycloakId) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(keycloakId)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private User userWithKeycloakId(String keycloakId) {
        return User.builder().id(targetId).username("target").keycloakId(keycloakId).build();
    }
}
