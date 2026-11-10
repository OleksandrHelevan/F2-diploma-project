package com.bricklayers.userservice.overview;

import com.bricklayers.userservice.dto.UserDto;

public record AccountOverviewDto(
        UserDto user,
        KeycloakAccountDto keycloak,
        AuditSummaryDto audit,
        long elapsedMs
) {
}
