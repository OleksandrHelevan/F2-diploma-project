package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import org.aspectj.lang.JoinPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KeycloakLinkAspectTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JoinPoint joinPoint;

    @InjectMocks
    private KeycloakLinkAspect aspect;

    private final UUID userId = UUID.randomUUID();

    @Test
    void unlinkedUser_rejectsWithConflict() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId, "request"});
        when(userRepository.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).build()));

        assertThatThrownBy(() -> aspect.requireKeycloakLink(joinPoint))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void linkedUser_passes() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId});
        when(userRepository.findById(userId))
                .thenReturn(Optional.of(User.builder().id(userId).keycloakId("keycloak-id").build()));

        assertThatCode(() -> aspect.requireKeycloakLink(joinPoint)).doesNotThrowAnyException();
    }

    @Test
    void unknownUser_passesSoMethodCanReportNotFound() {
        when(joinPoint.getArgs()).thenReturn(new Object[]{userId});
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatCode(() -> aspect.requireKeycloakLink(joinPoint)).doesNotThrowAnyException();
    }
}
