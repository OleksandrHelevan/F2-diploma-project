package com.bricklayers.userservice.db;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Slf4j
public class ReadYourWritesTracker {

    static final String KEY_PREFIX = "db:sticky:v1:";
    private static final Object REGISTERED = new Object();
    private static final Duration CIRCUIT_OPEN_FOR = Duration.ofSeconds(5);

    private final StringRedisTemplate redis;
    private final Duration ttl;
    private final Clock clock;
    private volatile Instant circuitOpenUntil = Instant.MIN;

    public ReadYourWritesTracker(StringRedisTemplate redis, Duration ttl) {
        this(redis, ttl, Clock.systemUTC());
    }

    ReadYourWritesTracker(StringRedisTemplate redis, Duration ttl, Clock clock) {
        this.redis = redis;
        this.ttl = ttl;
        this.clock = clock;
    }

    public boolean enabled() {
        return ttl != null && !ttl.isZero() && !ttl.isNegative();
    }

    public Duration ttl() {
        return ttl;
    }

    public void onPrimaryTransaction() {
        if (!enabled()
                || !TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(REGISTERED)) {
            return;
        }
        String subject = currentSubject();
        if (subject == null) {
            return;
        }
        TransactionSynchronizationManager.bindResource(REGISTERED, Boolean.TRUE);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                markWrite(subject);
            }

            @Override
            public void afterCompletion(int status) {
                if (TransactionSynchronizationManager.hasResource(REGISTERED)) {
                    TransactionSynchronizationManager.unbindResource(REGISTERED);
                }
            }
        });
    }

    public void markWrite(String subject) {
        if (!enabled() || circuitOpen()) {
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + subject, Instant.now(clock).toString(), ttl);
        } catch (RuntimeException exception) {
            openCircuit(exception);
        }
    }

    public boolean isSticky(String subject) {
        if (!enabled() || subject == null) {
            return false;
        }
        if (circuitOpen()) {
            return true;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + subject));
        } catch (RuntimeException exception) {
            openCircuit(exception);
            return true;
        }
    }

    public static String currentSubject() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getSubject();
        }
        return null;
    }

    private boolean circuitOpen() {
        return Instant.now(clock).isBefore(circuitOpenUntil);
    }

    private void openCircuit(RuntimeException exception) {
        circuitOpenUntil = Instant.now(clock).plus(CIRCUIT_OPEN_FOR);
        log.warn("Redis is unavailable for read-your-writes tracking, reads go to the primary for {} s: {}",
                CIRCUIT_OPEN_FOR.toSeconds(), exception.toString());
    }
}
