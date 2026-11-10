package com.bricklayers.userservice.cache;

import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.exception.UserNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> values;

    private final JsonMapper json = JsonMapper.builder().build();
    private final UUID userId = UUID.randomUUID();
    private final UserDto profile = new UserDto(userId, "worker", "worker@bricklayers.local", "Ivan", "Builder",
            Role.BUILDER, UUID.randomUUID(), true, LocalDateTime.of(2026, 11, 3, 12, 0), null);
    private final AtomicInteger databaseReads = new AtomicInteger();
    private final Supplier<UserDto> database = () -> {
        databaseReads.incrementAndGet();
        return profile;
    };
    private MockHttpServletResponse response;
    private UserProfileCache cache;

    @BeforeEach
    void setUp() {
        cache = new UserProfileCache(redis, json, true, 300);
        response = new MockHttpServletResponse();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest(), response));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void missStoresProfileWithTtlAndHitSkipsDatabase() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(UserProfileCache.idKey(userId))).thenReturn(null);

        assertThat(cache.byId(userId, database)).isEqualTo(profile);
        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("MISS");

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(eq(UserProfileCache.idKey(userId)), stored.capture(), ttl.capture());
        assertThat(ttl.getValue()).isBetween(Duration.ofSeconds(300), Duration.ofSeconds(330));

        when(values.get(UserProfileCache.idKey(userId))).thenReturn(stored.getValue());
        assertThat(cache.byId(userId, database)).isEqualTo(profile);
        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("HIT");
        assertThat(databaseReads).hasValue(1);
    }

    @Test
    void redisUnavailableFallsBackToDatabase() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(cache.byKeycloakId("kc-1", database)).isEqualTo(profile);
        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("BYPASS");
        assertThat(databaseReads).hasValue(1);
    }

    @Test
    void afterRedisFailureReadsSkipRedisUntilRetryInterval() {
        UserProfileCache breaker = new UserProfileCache(redis, json, true, 300, Duration.ofMinutes(1));
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        breaker.byId(userId, database);
        breaker.byId(userId, database);
        breaker.byId(userId, database);

        verify(redis, times(1)).opsForValue();
        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("BYPASS");
        assertThat(databaseReads).hasValue(3);
    }

    @Test
    void readsUseRedisAgainAfterRetryInterval() {
        UserProfileCache breaker = new UserProfileCache(redis, json, true, 300, Duration.ZERO);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down")).thenReturn(null);

        breaker.byId(userId, database);
        breaker.byId(userId, database);

        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("MISS");
    }

    @Test
    void failedCacheWriteReturnsDatabaseResultWithoutSecondQuery() {
        when(redis.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("down"))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        assertThat(cache.byId(userId, database)).isEqualTo(profile);
        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("BYPASS");
        assertThat(databaseReads).hasValue(1);
    }

    @Test
    void databaseErrorsPropagateAndAreNotCached() {
        when(redis.opsForValue()).thenReturn(values);

        assertThatThrownBy(() -> cache.byId(userId, () -> {
            throw new UserNotFoundException("user.not.found", userId);
        })).isInstanceOf(UserNotFoundException.class);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void disabledCacheReadsDatabaseOnly() {
        UserProfileCache disabled = new UserProfileCache(redis, json, false, 300);

        assertThat(disabled.byId(userId, database)).isEqualTo(profile);
        assertThat(response.getHeader(UserProfileCache.HEADER)).isEqualTo("BYPASS");
        disabled.evict(userId, "kc-1");
        verify(redis, never()).opsForValue();
        verify(redis, never()).delete(any(List.class));
    }

    @Test
    void evictOutsideTransactionDeletesBothKeysImmediately() {
        cache.evict(userId, "kc-1");

        verify(redis).delete(List.of(UserProfileCache.idKey(userId), UserProfileCache.keycloakKey("kc-1")));
    }

    @Test
    void evictInsideTransactionWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();

        cache.evict(userId, "kc-1");
        verify(redis, never()).delete(any(List.class));

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(redis).delete(List.of(UserProfileCache.idKey(userId), UserProfileCache.keycloakKey("kc-1")));
    }

    @Test
    void failedInvalidationDoesNotFailTheCaller() {
        when(redis.delete(any(List.class))).thenThrow(new RedisConnectionFailureException("down"));

        cache.evict(userId, null);

        verify(redis).delete(List.of(UserProfileCache.idKey(userId)));
    }
}
