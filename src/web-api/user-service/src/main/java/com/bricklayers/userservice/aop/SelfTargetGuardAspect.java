package com.bricklayers.userservice.aop;

import com.bricklayers.userservice.annotation.ForbidSelfTarget;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.repository.UserRepository;
import com.bricklayers.userservice.security.AuthenticatedActor;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

/**
 * Prevents an administrator from applying a destructive action to their own account
 * (e.g. deleting themselves and locking everyone out of the admin panel).
 * Calls without an authenticated JWT (scheduler, internal jobs) are not restricted.
 */
@Aspect
@Component
@Order(SelfTargetGuardAspect.ORDER)
@RequiredArgsConstructor
public class SelfTargetGuardAspect {

    static final int ORDER = 2;

    private final UserRepository userRepository;

    @Around("@annotation(guard)")
    public Object rejectSelfTarget(ProceedingJoinPoint joinPoint, ForbidSelfTarget guard) throws Throwable {
        Optional<String> actorKeycloakId = AuthenticatedActor.keycloakId();
        Optional<UUID> targetId = JoinPointArguments.firstUuid(joinPoint);
        if (actorKeycloakId.isPresent() && targetId.isPresent()) {
            boolean selfTarget = userRepository.findById(targetId.get())
                    .map(User::getKeycloakId)
                    .filter(actorKeycloakId.get()::equals)
                    .isPresent();
            if (selfTarget) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, guard.message());
            }
        }
        return joinPoint.proceed();
    }
}
