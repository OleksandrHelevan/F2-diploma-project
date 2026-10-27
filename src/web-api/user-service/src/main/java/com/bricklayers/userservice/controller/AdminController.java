package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.annotation.SuperAdminDelete;
import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.repository.UserRepository;
import com.bricklayers.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/admin/users")
@SecurityRequirement(name = "keycloak", scopes = {"openid", "profile", "email"})
@RequiredArgsConstructor
public class AdminController {

    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "userCount", userRepository.count(),
                "message", "Admin-only statistics"
        );
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Page<UserDto>> getUsers(@RequestParam int page, @RequestParam int size) {
        return ResponseEntity.ok(userService.getUsers(page, size));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserDto> update(@PathVariable UUID id,
                                          @Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(userService.update(id, request));
    }

    @SuperAdminDelete("/{id}")
    public void delete(@PathVariable UUID id) {
        userService.delete(id);
    }

}
