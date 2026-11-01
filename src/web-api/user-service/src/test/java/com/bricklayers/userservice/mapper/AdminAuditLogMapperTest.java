package com.bricklayers.userservice.mapper;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import com.bricklayers.userservice.entity.AdminAuditLog;
import com.bricklayers.userservice.entity.AuditOutcome;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAuditLogMapperTest {

    private final AdminAuditLogMapper mapper = new AdminAuditLogMapperImpl();

    @Test
    void toDto_copiesAllFields() {
        UUID id = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 30, 9, 0);
        AdminAuditLog log = AdminAuditLog.builder()
                .id(id)
                .action("USER_DELETE")
                .actor("root(admin-keycloak-id)")
                .target("target-id")
                .outcome(AuditOutcome.FAILURE)
                .reason("ResponseStatusException")
                .createdAt(createdAt)
                .build();

        AdminAuditLogDto dto = mapper.toDto(log);

        assertThat(dto).isEqualTo(new AdminAuditLogDto(id, "USER_DELETE", "root(admin-keycloak-id)",
                "target-id", AuditOutcome.FAILURE, "ResponseStatusException", createdAt));
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }
}
