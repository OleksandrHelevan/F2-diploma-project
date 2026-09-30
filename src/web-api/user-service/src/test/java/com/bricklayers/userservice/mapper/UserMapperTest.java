package com.bricklayers.userservice.mapper;

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private final UserMapper userMapper = Mappers.getMapper(UserMapper.class);

    @Test
    void toDto_mapsEntityFields() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        LocalDateTime updatedAt = LocalDateTime.of(2025, 1, 3, 4, 5, 6);

        User user = User.builder()
                .id(userId)
                .username("builder")
                .email("builder@example.com")
                .firstName("Ivan")
                .lastName("Builder")
                .role(Role.BUILDER)
                .companyId(companyId)
                .keycloakId("keycloak-123")
                .active(true)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .build();

        UserDto dto = userMapper.toDto(user);

        assertThat(dto).isEqualTo(new UserDto(
                userId,
                "builder",
                "builder@example.com",
                "Ivan",
                "Builder",
                Role.BUILDER,
                companyId,
                true,
                createdAt,
                updatedAt));
    }

    @Test
    void toEntity_mapsRequestFields() {
        CreateUserRequest request = new CreateUserRequest(
                "builder",
                "builder@example.com",
                "Ivan",
                "Builder",
                Role.BUILDER,
                UUID.randomUUID());

        User user = userMapper.toEntity(request);

        assertThat(user.getUsername()).isEqualTo("builder");
        assertThat(user.getEmail()).isEqualTo("builder@example.com");
        assertThat(user.getFirstName()).isEqualTo("Ivan");
        assertThat(user.getLastName()).isEqualTo("Builder");
        assertThat(user.getRole()).isEqualTo(Role.BUILDER);
        assertThat(user.getCompanyId()).isEqualTo(request.companyId());
        assertThat(user.getId()).isNull();
        assertThat(user.getKeycloakId()).isNull();
        assertThat(user.isActive()).isFalse();
        assertThat(user.getCreatedAt()).isNull();
        assertThat(user.getUpdatedAt()).isNull();
    }

    @Test
    void updateEntityFromRequest_updatesOnlyNonNullValues() {
        UUID companyId = UUID.randomUUID();
        User user = User.builder()
                .id(UUID.randomUUID())
                .username("builder")
                .email("old@example.com")
                .firstName("Old")
                .lastName("Name")
                .role(Role.BUILDER)
                .companyId(companyId)
                .keycloakId("kc-old")
                .active(true)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now().minusHours(1))
                .build();

        UpdateUserRequest request = new UpdateUserRequest(
                "New",
                null,
                "new@example.com",
                Role.FOREMAN,
                null,
                false);

        userMapper.updateEntityFromRequest(request, user);

        assertThat(user.getFirstName()).isEqualTo("New");
        assertThat(user.getLastName()).isEqualTo("Name");
        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getRole()).isEqualTo(Role.FOREMAN);
        assertThat(user.getCompanyId()).isEqualTo(companyId);
        assertThat(user.getUsername()).isEqualTo("builder");
        assertThat(user.getKeycloakId()).isEqualTo("kc-old");
        assertThat(user.isActive()).isFalse();
    }
}
