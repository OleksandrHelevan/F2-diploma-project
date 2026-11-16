package com.bricklayers.notificationservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.Map;

@Configuration
public class SecurityConfig {

    static final String AUDIENCE = "notification-service";
    /** Keycloak client whose service account (user-service) hands e-mails over after commit. */
    static final String SERVICE_CLIENT = "user-sync-service";
    /** Realm role allowed to call the API by hand, e.g. from Swagger UI. */
    static final String ADMIN_ROLE = "SUPERADMIN";

    @Bean
    SecurityFilterChain notificationSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health", "/error",
                                "/notification-service/swagger-ui.html",
                                "/notification-service/swagger-ui/**",
                                "/notification-service/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/v1/notifications/**").access((authentication, context) ->
                                new AuthorizationDecision(authentication.get() instanceof JwtAuthenticationToken token
                                        && isServiceOrAdmin(token.getToken())))
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> { }));
        return http.build();
    }

    /**
     * The audience check alone is not enough once Swagger's client also carries the notification-service audience:
     * only the user-service service account and SUPERADMIN may make the system send e-mails.
     */
    static boolean isServiceOrAdmin(Jwt jwt) {
        if (SERVICE_CLIENT.equals(jwt.getClaimAsString("azp"))) {
            return true;
        }
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        return realmAccess != null
                && realmAccess.get("roles") instanceof Collection<?> roles
                && roles.contains(ADMIN_ROLE);
    }

    @Bean
    JwtDecoder notificationJwtDecoder(@Value("${keycloak.issuer-uri}") String issuerUri,
                                      @Value("${keycloak.jwk-set-uri}") String jwkSetUri) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                audienceValidator()));
        return decoder;
    }

    static OAuth2TokenValidator<Jwt> audienceValidator() {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Token audience does not include " + AUDIENCE, null));
    }
}
