package com.bricklayers.userservice.dto;

import com.bricklayers.userservice.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateUserRequest(

        @NotBlank(message = "{validation.username.required}")
        @Size(min = 3, max = 100, message = "{validation.username.size}")
        String username,

        @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,

        @Size(max = 100, message = "{validation.firstName.size}")
        String firstName,

        @Size(max = 100, message = "{validation.lastName.size}")
        String lastName,

        @NotNull(message = "{validation.role.required}")
        Role role,

        UUID companyId
) {
}