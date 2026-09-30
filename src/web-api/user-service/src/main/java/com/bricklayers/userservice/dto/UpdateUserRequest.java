package com.bricklayers.userservice.dto;

import com.bricklayers.userservice.entity.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateUserRequest(

        @Size(max = 100, message = "{validation.firstName.size}")
        String firstName,

        @Size(max = 100, message = "{validation.lastName.size}")
        String lastName,

        @Email(message = "{validation.email.invalid}")
        String email,

        Role role,

        UUID companyId,

        Boolean active
) {
}