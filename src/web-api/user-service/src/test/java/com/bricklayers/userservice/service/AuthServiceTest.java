package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.LoginRequest;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtEncoder jwtEncoder;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setIssuer() {
        ReflectionTestUtils.setField(authService, "issuer", "bricklayers-user-service");
    }

    @Test
    void loginAuthenticatesAgainstLocalPasswordHashAndIssuesLocalSubject() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .active(true)
                .passwordHash("$2a$local-hash")
                .build();
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("local-jwt")
                .header("alg", "HS256")
                .issuer("bricklayers-user-service")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(900))
                .build();
        when(userRepository.findAllByUsernameOrEmail("builder", "builder")).thenReturn(List.of(user));
        when(passwordEncoder.matches("password123", user.getPasswordHash())).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt);

        var response = authService.login(new LoginRequest("builder", "password123"));

        assertThat(response.accessToken()).isEqualTo("local-jwt");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(900);
        ArgumentCaptor<JwtEncoderParameters> parameters =
                ArgumentCaptor.forClass(JwtEncoderParameters.class);
        verify(jwtEncoder).encode(parameters.capture());
        assertThat(parameters.getValue().getClaims().getSubject()).isEqualTo(userId.toString());
        assertThat(parameters.getValue().getClaims().getClaimAsString("iss"))
                .isEqualTo("bricklayers-user-service");
        Object realmAccess = parameters.getValue().getClaims().getClaim("realm_access");
        assertThat(realmAccess).isEqualTo(java.util.Map.of("roles", List.of("BUILDER")));
    }

    @Test
    void loginRejectsUserWithoutLocalPasswordHash() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .active(true)
                .build();
        when(userRepository.findAllByUsernameOrEmail("builder", "builder")).thenReturn(List.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("builder", "password123")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");

        verify(passwordEncoder, never()).matches(any(), any());
        verify(jwtEncoder, never()).encode(any());
    }

    @Test
    void loginRejectsInvalidPassword() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .active(true)
                .passwordHash("$2a$local-hash")
                .build();
        when(userRepository.findAllByUsernameOrEmail("builder", "builder")).thenReturn(List.of(user));
        when(passwordEncoder.matches("wrong", user.getPasswordHash())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("builder", "wrong")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
        verify(jwtEncoder, never()).encode(any());
    }
}
