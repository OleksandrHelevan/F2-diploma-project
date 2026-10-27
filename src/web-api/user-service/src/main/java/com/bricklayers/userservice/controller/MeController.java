package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.annotation.CurrentUserId;
import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;


@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "keycloak", scopes = {"openid", "profile", "email"})
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @GetMapping("/by-keycloak-id/{keycloakId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getByKeycloakId(@PathVariable String keycloakId) {
        return ResponseEntity.ok(userService.getByKeycloakId(keycloakId));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> me(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }

    @PutMapping("/set-password")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public void setPassword(@CurrentUserId UUID userId,
                            @Valid @RequestBody SetPasswordRequest request) {
        userService.setPassword(userId, request);
    }
}