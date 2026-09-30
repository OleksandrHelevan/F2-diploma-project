package com.bricklayers.userservice.dto;

import com.bricklayers.userservice.entity.Role;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        Role role,
        UUID companyId,
        boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}