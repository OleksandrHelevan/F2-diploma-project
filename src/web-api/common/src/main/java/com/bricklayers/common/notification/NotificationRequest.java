package com.bricklayers.common.notification;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record NotificationRequest(
        @NotNull UUID id,
        @NotNull NotificationType type,
        @NotBlank @Email String recipient,
        Map<String, String> params
) {
}
