package com.bricklayers.userservice.security;

import com.bricklayers.userservice.annotation.CurrentUserId;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrentUserIdArgumentResolverTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CurrentUserIdArgumentResolver resolver;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesLocalIdFromKeycloakJwtSubject() throws Exception {
        UUID localUserId = UUID.randomUUID();
        User user = User.builder().id(localUserId).keycloakId("keycloak-user-id").build();
        when(userRepository.findByKeycloakId("keycloak-user-id")).thenReturn(Optional.of(user));
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("keycloak-user-id")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        MethodParameter parameter = currentUserIdParameter();
        Object resolved = resolver.resolveArgument(
                parameter, null, new ServletWebRequest(new MockHttpServletRequest()), null);

        assertThat(resolver.supportsParameter(parameter)).isTrue();
        assertThat(resolved).isEqualTo(localUserId);
    }

    @Test
    void rejectsKeycloakUserWithoutLocalProfile() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("keycloak-user-id")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        assertThatThrownBy(() -> resolver.resolveArgument(
                currentUserIdParameter(), null,
                new ServletWebRequest(new MockHttpServletRequest()), null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated Keycloak user is not linked to a local profile");
    }

    private MethodParameter currentUserIdParameter() throws NoSuchMethodException {
        return new MethodParameter(TestController.class.getDeclaredMethod("me", UUID.class), 0);
    }

    private static class TestController {
        @SuppressWarnings("unused")
        void me(@CurrentUserId UUID userId) {}
    }
}
