package com.bricklayers.userservice.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadYourWritesTrackerTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final ReadYourWritesTracker tracker = new ReadYourWritesTracker(redis, Duration.ofSeconds(5));

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                Jwt.withTokenValue("t").header("alg", "none").subject("kc-admin").build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.clear();
    }

    @Test
    void writeTransactionOfAnAuthenticatedUser_marksThemStickyAfterCommit() {
        startTransaction(false);

        tracker.onPrimaryTransaction();
        tracker.onPrimaryTransaction();

        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        assertThat(synchronizations).hasSize(1);
        verify(values, never()).set(anyString(), anyString(), eq(Duration.ofSeconds(5)));

        synchronizations.forEach(TransactionSynchronization::afterCommit);
        synchronizations.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));

        verify(values).set(eq("db:sticky:v1:kc-admin"), anyString(), eq(Duration.ofSeconds(5)));
    }

    @Test
    void readOnlyTransactionOrNoUser_isNotTracked() {
        startTransaction(true);
        tracker.onPrimaryTransaction();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();

        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        SecurityContextHolder.clearContext();
        tracker.onPrimaryTransaction();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void stickyWindowIsReadFromRedis() {
        when(redis.hasKey("db:sticky:v1:kc-admin")).thenReturn(true);
        when(redis.hasKey("db:sticky:v1:kc-worker")).thenReturn(false);

        assertThat(tracker.isSticky("kc-admin")).isTrue();
        assertThat(tracker.isSticky("kc-worker")).isFalse();
        assertThat(tracker.isSticky(null)).isFalse();
    }

    @Test
    void redisFailure_routesToThePrimaryAndOpensTheCircuit() {
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(tracker.isSticky("kc-admin")).isTrue();
        assertThat(tracker.isSticky("kc-worker")).isTrue();
        tracker.markWrite("kc-admin");

        verify(redis, times(1)).hasKey(anyString());
        verify(values, never()).set(anyString(), anyString(), eq(Duration.ofSeconds(5)));
    }

    @Test
    void zeroTtl_disablesStickyRouting() {
        ReadYourWritesTracker disabled = new ReadYourWritesTracker(redis, Duration.ZERO);
        startTransaction(false);

        disabled.onPrimaryTransaction();
        disabled.markWrite("kc-admin");

        assertThat(disabled.enabled()).isFalse();
        assertThat(disabled.isSticky("kc-admin")).isFalse();
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(redis, never()).hasKey(anyString());
    }

    private static void startTransaction(boolean readOnly) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
    }
}
