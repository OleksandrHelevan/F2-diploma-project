package com.bricklayers.userservice.cache;

import com.bricklayers.userservice.dto.UserDto;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  UserProfileCache
  @version  1.0.0
  @since 03.11.2026 - 14.00
*/


@Slf4j
@Component
public class UserProfileCache {

    public static final String HEADER = "X-Cache";
    static final String PREFIX = "user:profile:v1:";

    private final StringRedisTemplate redis;
    private final JsonMapper json;
    private final boolean enabled;
    private final Duration ttl;
    private final long retryAfterNanos;
    private volatile long bypassUntilNanos = System.nanoTime();

    @Autowired
    public UserProfileCache(StringRedisTemplate redis,
                            JsonMapper json,
                            @Value("${app.cache.enabled:false}") boolean enabled,
                            @Value("${app.cache.ttl-seconds:300}") long ttlSeconds) {
        this(redis, json, enabled, ttlSeconds, Duration.ofSeconds(5));
    }

    UserProfileCache(StringRedisTemplate redis, JsonMapper json, boolean enabled, long ttlSeconds,
                     Duration retryAfter) {
        if (ttlSeconds < 1 || ttlSeconds > 86_400) {
            throw new IllegalArgumentException("CACHE_TTL_SECONDS must be between 1 and 86400");
        }
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
        this.ttl = Duration.ofSeconds(ttlSeconds);
        this.retryAfterNanos = retryAfter.toNanos();
    }

    public static String idKey(UUID id) {
        return PREFIX + "id:" + id;
    }

    public static String keycloakKey(String keycloakId) {
        return PREFIX + "kc:" + keycloakId;
    }

    public UserDto byId(UUID id, Supplier<UserDto> database) {
        return read(idKey(id), database);
    }

    public UserDto byKeycloakId(String keycloakId, Supplier<UserDto> database) {
        return read(keycloakKey(keycloakId), database);
    }

    private UserDto read(String key, Supplier<UserDto> database) {
        if (!enabled || System.nanoTime() - bypassUntilNanos < 0) {
            report("BYPASS");
            return database.get();
        }
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null) {
                UserDto value = json.readValue(cached, UserDto.class);
                log.debug("User profile cache HIT {}", key);
                report("HIT");
                return value;
            }
        } catch (RuntimeException exception) {
            log.debug("User profile cache unavailable, reading PostgreSQL", exception);
            openCircuit();
            report("BYPASS");
            return database.get();
        }

        UserDto value = database.get();
        try {
            redis.opsForValue().set(key, json.writeValueAsString(value), ttlWithJitter());
            log.debug("User profile cache MISS {}", key);
            report("MISS");
        } catch (RuntimeException exception) {
            log.debug("User profile cache write failed, returning PostgreSQL result", exception);
            openCircuit();
            report("BYPASS");
        }
        return value;
    }

    private void openCircuit() {
        bypassUntilNanos = System.nanoTime() + retryAfterNanos;
    }

    public void evict(UUID id, String keycloakId) {
        if (!enabled) {
            return;
        }
        List<String> keys = keycloakId == null || keycloakId.isBlank()
                ? List.of(idKey(id))
                : List.of(idKey(id), keycloakKey(keycloakId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(keys);
                }
            });
        } else {
            delete(keys);
        }
    }

    private void delete(List<String> keys) {
        try {
            redis.delete(keys);
            log.info("User profile cache INVALIDATED {}", keys);
        } catch (RuntimeException exception) {
            log.warn("User profile cache invalidation failed for {}, entries expire within {}", keys, ttl);
        }
    }

    private Duration ttlWithJitter() {
        long jitterMillis = ThreadLocalRandom.current().nextLong(ttl.toMillis() / 10 + 1);
        return ttl.plusMillis(jitterMillis);
    }

    private static void report(String status) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            HttpServletResponse response = attributes.getResponse();
            if (response != null && !response.isCommitted()) {
                response.setHeader(HEADER, status);
            }
        }
    }
}
