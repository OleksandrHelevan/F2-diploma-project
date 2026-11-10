package com.bricklayers.userservice.overview;

import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class AccountOverviewService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final AccountLookups lookups;
    private final Duration timeout;

    public AccountOverviewService(UserRepository userRepository,
                                  UserMapper userMapper,
                                  AccountLookups lookups,
                                  @Value("${app.async.overview-timeout:2s}") Duration timeout) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
        this.lookups = lookups;
        this.timeout = timeout;
    }

    public AccountOverviewDto overview(UUID userId) {
        long started = System.nanoTime();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user.not.found", userId));

        CompletableFuture<KeycloakAccountDto> keycloak = hasKeycloakLink(user)
                ? lookups.keycloakAccount(user.getKeycloakId())
                : CompletableFuture.completedFuture(KeycloakAccountDto.notLinked());
        CompletableFuture<AuditSummaryDto> audit = lookups.auditSummary(userId);

        CompletableFuture<KeycloakAccountDto> keycloakBounded = keycloak
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(KeycloakAccountDto::unavailable);
        CompletableFuture<AuditSummaryDto> auditBounded = audit
                .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .exceptionally(AuditSummaryDto::unavailable);

        return keycloakBounded
                .thenCombine(auditBounded, (keycloakAccount, auditSummary) -> new AccountOverviewDto(
                        userMapper.toDto(user),
                        keycloakAccount,
                        auditSummary,
                        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)))
                .join();
    }

    private static boolean hasKeycloakLink(User user) {
        return user.getKeycloakId() != null && !user.getKeycloakId().isBlank();
    }
}
