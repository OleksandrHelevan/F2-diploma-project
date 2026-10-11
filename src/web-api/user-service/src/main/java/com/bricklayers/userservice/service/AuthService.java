package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.LoginRequest;
import com.bricklayers.userservice.dto.TokenResponse;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/*
  @author   user
  @project   F2-diploma-project
  @class  AuthService
  @version  1.0.0 
  @since 09.10.2026 - 00.03
*/

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long TOKEN_LIFETIME_SECONDS = 900;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    @Value("${local-auth.jwt.issuer}")
    private String issuer;

    public TokenResponse login(LoginRequest request) {
        List<User> matches = userRepository.findAllByUsernameOrEmail(request.username(), request.username());
        if (matches.size() != 1) {
            throw invalidCredentials();
        }
        User user = matches.getFirst();
        if (!user.isActive()
                || user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }

        Instant issuedAt = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(TOKEN_LIFETIME_SECONDS, ChronoUnit.SECONDS))
                .subject(user.getId().toString())
                .claim("preferred_username", user.getUsername())
                .claim("email", user.getEmail())
                .claim("realm_access", Map.of("roles", List.of(user.getRole().name())))
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", TOKEN_LIFETIME_SECONDS);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Невірний логін або пароль");
    }
}