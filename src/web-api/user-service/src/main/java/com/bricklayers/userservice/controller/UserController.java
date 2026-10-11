package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.annotation.CurrentUserId;
import com.bricklayers.userservice.annotation.SuperAdminDelete;
import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest request) {
        UserDto created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<List<UserDto>> getAll() {
        return ResponseEntity.ok(userService.getAll());
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

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserDto> me(@CurrentUserId UUID userId) {
        return ResponseEntity.ok(userService.getById(userId));
    }
}