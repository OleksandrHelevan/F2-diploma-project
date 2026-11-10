package com.bricklayers.userservice.overview;

import com.bricklayers.userservice.dto.AdminAuditLogDto;

import java.util.List;

public record AuditSummaryDto(
        boolean available,
        long totalActions,
        List<AdminAuditLogDto> recent,
        String loadedBy,
        String error
) {

    public static AuditSummaryDto loaded(long totalActions, List<AdminAuditLogDto> recent) {
        return new AuditSummaryDto(true, totalActions, recent, Thread.currentThread().getName(), null);
    }

    public static AuditSummaryDto unavailable(Throwable failure) {
        return new AuditSummaryDto(false, 0, List.of(), null, "Audit history unavailable: " + Failures.describe(failure));
    }
}
