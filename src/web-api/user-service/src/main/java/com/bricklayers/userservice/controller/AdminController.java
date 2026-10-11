package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
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
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user.not.found", userId));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
    }
}
