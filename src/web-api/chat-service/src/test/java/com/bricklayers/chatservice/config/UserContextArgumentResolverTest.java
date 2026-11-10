package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.ChatIdentityService;
import com.bricklayers.chatservice.security.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContextArgumentResolverTest {

    @Mock
    private ChatIdentityService identityService;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void supportsOnlyUserContextParameters() throws NoSuchMethodException {
        UserContextArgumentResolver resolver = new UserContextArgumentResolver(false, identityService);
        var method = UserContextArgumentResolverTest.class.getDeclaredMethod("handler", UserContext.class, String.class);

        assertThat(resolver.supportsParameter(new MethodParameter(method, 0))).isTrue();
        assertThat(resolver.supportsParameter(new MethodParameter(method, 1))).isFalse();
    }

    @Test
    void resolvesUserFromJwtAuthentication() {
        UserContextArgumentResolver resolver = new UserContextArgumentResolver(false, identityService);
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("kc").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        UserContext user = new UserContext("u1", "User", "c1", ParticipantRole.BUILDER);
        when(identityService.resolve(jwt)).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "spoofed");

        assertThat(resolver.resolveArgument(null, null, new ServletWebRequest(request), null)).isEqualTo(user);
    }

    @Test
    void rejectsMissingOrNonJwtAuthentication() {
        UserContextArgumentResolver resolver = new UserContextArgumentResolver(false, identityService);
        ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest());

        assertStatus(resolver, request, HttpStatus.UNAUTHORIZED);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("u", "p"));
        assertStatus(resolver, request, HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(identityService);
    }

    @Test
    void demoModeReadsUserFromHeaders() {
        UserContextArgumentResolver resolver = new UserContextArgumentResolver(true, identityService);
        MockHttpServletRequest request = demoRequest("FOREMAN");

        Object resolved = resolver.resolveArgument(null, null, new ServletWebRequest(request), null);

        assertThat(resolved).isEqualTo(new UserContext("demo-foreman", "Прораб", "demo-company", ParticipantRole.FOREMAN));
        verifyNoInteractions(identityService);
    }

    @Test
    void demoModeValidatesHeaders() {
        UserContextArgumentResolver resolver = new UserContextArgumentResolver(true, identityService);

        assertStatus(resolver, new ServletWebRequest(demoRequest("EMPEROR")), HttpStatus.BAD_REQUEST);

        MockHttpServletRequest badName = demoRequest("FOREMAN");
        badName.removeHeader("X-User-Name-Base64");
        badName.addHeader("X-User-Name-Base64", "***");
        assertStatus(resolver, new ServletWebRequest(badName), HttpStatus.BAD_REQUEST);

        MockHttpServletRequest missingCompany = demoRequest("FOREMAN");
        missingCompany.removeHeader("X-Company-Id");
        assertStatus(resolver, new ServletWebRequest(missingCompany), HttpStatus.UNAUTHORIZED);
    }

    private static MockHttpServletRequest demoRequest(String role) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "demo-foreman");
        request.addHeader("X-User-Name-Base64", Base64.getEncoder().encodeToString("Прораб".getBytes(StandardCharsets.UTF_8)));
        request.addHeader("X-Company-Id", "demo-company");
        request.addHeader("X-User-Role", role);
        return request;
    }

    private static void assertStatus(UserContextArgumentResolver resolver, ServletWebRequest request, HttpStatus status) {
        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }

    @SuppressWarnings("unused")
    private void handler(UserContext user, String other) {
    }
}
