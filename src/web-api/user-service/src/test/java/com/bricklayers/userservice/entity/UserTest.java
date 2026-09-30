package com.bricklayers.userservice.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    void onCreate_setsAuditFieldsAndActivatesUser() {
        User user = User.builder()
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .active(false)
                .build();

        user.onCreate();

        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();
        assertThat(user.getCreatedAt()).isEqualTo(user.getUpdatedAt());
        assertThat(user.isActive()).isTrue();
    }

    @Test
    void onUpdate_updatesUpdatedAtOnly() {
        User user = User.builder()
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .active(true)
                .createdAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .updatedAt(LocalDateTime.of(2025, 1, 1, 12, 0))
                .build();

        user.onUpdate();

        assertThat(user.getUpdatedAt()).isAfter(user.getCreatedAt());
    }
}
