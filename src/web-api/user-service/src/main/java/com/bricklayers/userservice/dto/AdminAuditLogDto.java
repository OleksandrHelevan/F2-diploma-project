package com.bricklayers.userservice.dto;

import com.bricklayers.userservice.entity.AuditOutcome;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminAuditLogDto(
        UUID id,
        String action,
        String actor,
        String target,
        AuditOutcome outcome,
        String reason,
        LocalDateTime createdAt
) {
}
