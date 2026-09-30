package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;


@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return Map.of(
                "userCount", userRepository.count(),
                "message", "Admin-only statistics"
        );
    }
}
