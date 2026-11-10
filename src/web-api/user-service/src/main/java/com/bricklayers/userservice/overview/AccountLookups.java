package com.bricklayers.userservice.overview;

import com.bricklayers.userservice.async.AsyncConfig;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakRole;
import com.bricklayers.userservice.mapper.AdminAuditLogMapper;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
public class AccountLookups {

    private final KeycloakAdminClient keycloakAdminClient;
    private final AdminAuditLogRepository auditLogRepository;
    private final AdminAuditLogMapper auditLogMapper;

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<KeycloakAccountDto> keycloakAccount(String keycloakId) {
        String accessToken = keycloakAdminClient.getAccessToken();
        List<String> roles = keycloakAdminClient.getRealmRoles(keycloakId, accessToken).stream()
                .map(KeycloakRole::name)
                .sorted()
                .toList();
        int sessions = keycloakAdminClient.countSessions(keycloakId, accessToken);
        return CompletableFuture.completedFuture(KeycloakAccountDto.loaded(roles, sessions));
    }

    @Async(AsyncConfig.EXECUTOR)
    public CompletableFuture<AuditSummaryDto> auditSummary(UUID userId) {
        String target = userId.toString();
        long total = auditLogRepository.countByTarget(target);
        var recent = auditLogRepository.findTop5ByTargetOrderByCreatedAtDesc(target).stream()
                .map(auditLogMapper::toDto)
                .toList();
        return CompletableFuture.completedFuture(AuditSummaryDto.loaded(total, recent));
    }
}
