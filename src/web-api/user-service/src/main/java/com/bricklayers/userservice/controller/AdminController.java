package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/admin")
@SecurityRequirement(name = "keycloak", scopes = {"openid", "profile", "email"})
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final KeycloakAdminClient keycloakAdminClient;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "userCount", userRepository.count(),
                "message", "Admin-only statistics"
        );
    }

    @PutMapping("/users/{userId}/password")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public void setPassword(@PathVariable UUID userId,
                            @Valid @RequestBody SetPasswordRequest request) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user.not.found", userId));
        if (user.getKeycloakId() == null || user.getKeycloakId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Користувача не пов'язано з обліковим записом Keycloak");
        }
        String accessToken = keycloakAdminClient.getAccessToken();
        keycloakAdminClient.updatePassword(user.getKeycloakId(), request.password(), accessToken);
    }
}
