package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.annotation.AuditAdminAction;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.AdminAuditLog;
import com.bricklayers.userservice.entity.AuditOutcome;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import com.bricklayers.userservice.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Audit trail for administrative actions. Runs outermost so that rejections made by
 * the guard aspects are recorded as failures too. Every record is written to the AUDIT log
 * and stored in {@code admin_audit_log}; a storage failure never changes the action's outcome.
 */
@Aspect
@Component
@Order(AdminAuditAspect.ORDER)
@RequiredArgsConstructor
public class AdminAuditAspect {

    static final int ORDER = 1;

    private static final Logger AUDIT = LoggerFactory.getLogger("AUDIT");

    private final AdminAuditLogRepository auditLogRepository;

    @AfterReturning(pointcut = "@annotation(audit)", returning = "result")
    public void recordSuccess(JoinPoint joinPoint, AuditAdminAction audit, Object result) {
        String actor = AuthenticatedActor.describe();
        String target = target(joinPoint, result);
        AUDIT.info("action={} actor={} target={} outcome=SUCCESS", audit.value(), actor, target);
        store(audit.value(), actor, target, AuditOutcome.SUCCESS, null);
    }

    @AfterThrowing(pointcut = "@annotation(audit)", throwing = "exception")
    public void recordFailure(JoinPoint joinPoint, AuditAdminAction audit, Throwable exception) {
        String actor = AuthenticatedActor.describe();
        String target = target(joinPoint, null);
        String reason = exception.getClass().getSimpleName();
        AUDIT.warn("action={} actor={} target={} outcome=FAILURE reason={}", audit.value(), actor, target, reason);
        store(audit.value(), actor, target, AuditOutcome.FAILURE, reason);
    }

    private void store(String action, String actor, String target, AuditOutcome outcome, String reason) {
        try {
            auditLogRepository.save(AdminAuditLog.builder()
                    .action(action)
                    .actor(actor)
                    .target(target)
                    .outcome(outcome)
                    .reason(reason)
                    .build());
        } catch (RuntimeException e) {
            AUDIT.error("Failed to store audit record action={} target={}", action, target, e);
        }
    }

    private static String target(JoinPoint joinPoint, Object result) {
        return JoinPointArguments.firstUuid(joinPoint)
                .or(() -> result instanceof UserDto dto ? Optional.ofNullable(dto.id()) : Optional.empty())
                .map(UUID::toString)
                .orElse("-");
    }
}
