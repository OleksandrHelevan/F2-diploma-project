package com.bricklayers.userservice.service.impl;

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserAlreadyExistsException;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private KeycloakAdminClient keycloakAdminClient;

    @InjectMocks
    private UserServiceImpl userService;

    private UUID userId;
    private User user;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        user = User.builder()
                .id(userId)
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .keycloakId("keycloak-user-id")
                .active(true)
                .build();
        userDto = new UserDto(
                userId,
                "builder",
                "builder@example.com",
                "Ivan",
                "Builder",
                Role.BUILDER,
                null,
                true,
                null,
                null);
    }

    @Test
    void create_validRequest_savesAndReturnsMappedUser() {
        CreateUserRequest request = new CreateUserRequest(
                "builder", "builder@example.com", "Ivan", "Builder", Role.BUILDER, null);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userMapper.toEntity(request)).thenReturn(user);
        when(keycloakAdminClient.getAccessToken()).thenReturn("service-token");
        when(keycloakAdminClient.createUser(
                request.username(), request.email(), null, "Ivan", "Builder", null, "service-token"))
                .thenReturn("created-keycloak-id");
        when(keycloakAdminClient.getRealmRole("BUILDER", "service-token"))
                .thenReturn(new KeycloakAdminClient.KeycloakRealmRole("builder-role-id", "BUILDER"));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.create(request);

        assertThat(result).isEqualTo(userDto);
        verify(userRepository).saveAndFlush(user);
        assertThat(user.getKeycloakId()).isEqualTo("created-keycloak-id");
        verify(keycloakAdminClient).assignRealmRole(
                "created-keycloak-id",
                new KeycloakAdminClient.KeycloakRealmRole("builder-role-id", "BUILDER"),
                "service-token");
        verify(userMapper).toEntity(request);
        verify(userMapper).toDto(user);
    }

    @Test
    void create_existingUsername_throwsAndDoesNotSave() {
        CreateUserRequest request = new CreateUserRequest(
                "builder", "new@example.com", null, null, Role.BUILDER, null);
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("user.already.exists.username");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void create_existingEmail_throwsAndDoesNotSave() {
        CreateUserRequest request = new CreateUserRequest(
                "newbuilder", "builder@example.com", null, null, Role.BUILDER, null);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("user.already.exists.email");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void getById_existingUser_returnsMappedDto() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.getById(userId);

        assertThat(result).isEqualTo(userDto);
        verify(userRepository).findById(userId);
        verify(userMapper).toDto(user);
    }

    @Test
    void getById_unknownUser_throwsNotFound() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getById(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("user.not.found");
        verify(userMapper, never()).toDto(any(User.class));
    }

    @Test
    void getByKeycloakId_returnsLinkedLocalProfile() {
        when(userRepository.findByKeycloakId("keycloak-user-id")).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(userDto);

        assertThat(userService.getByKeycloakId("keycloak-user-id")).isEqualTo(userDto);
    }


    @Test
    void update_changedEmail_savesUpdatedEntityAndReturnsDto() {
        UpdateUserRequest request = new UpdateUserRequest(
                "Ivan", "Builder", "updated@example.com", Role.FOREMAN, null, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(keycloakAdminClient.getAccessToken()).thenReturn("service-token");
        when(keycloakAdminClient.getRealmRoles("keycloak-user-id", "service-token"))
                .thenReturn(List.of(new KeycloakAdminClient.KeycloakRole("builder-role-id", "BUILDER")));
        when(keycloakAdminClient.getRealmRole("FOREMAN", "service-token"))
                .thenReturn(new KeycloakAdminClient.KeycloakRealmRole("foreman-role-id", "FOREMAN"));
        doAnswer(invocation -> {
            user.setFirstName(request.firstName());
            user.setLastName(request.lastName());
            user.setEmail(request.email());
            user.setRole(request.role());
            user.setCompanyId(request.companyId());
            user.setActive(request.active());
            return null;
        }).when(userMapper).updateEntityFromRequest(request, user);
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.update(userId, request);

        assertThat(result).isEqualTo(userDto);
        verify(userMapper).updateEntityFromRequest(request, user);
        verify(keycloakAdminClient).updateUser(
                "keycloak-user-id",
                new KeycloakAdminClient.KeycloakUserRepresentation(
                        "builder", "updated@example.com", "Ivan", "Builder", true, null,
                        null, Map.of()),
                "service-token");
        verify(keycloakAdminClient).removeRealmRole(
                "keycloak-user-id",
                new KeycloakAdminClient.KeycloakRole("builder-role-id", "BUILDER"),
                "service-token");
        verify(keycloakAdminClient).assignRealmRole(
                "keycloak-user-id",
                new KeycloakAdminClient.KeycloakRealmRole("foreman-role-id", "FOREMAN"),
                "service-token");
        verify(userRepository).saveAndFlush(user);
        verify(userMapper).toDto(user);
    }

    @Test
    void update_duplicateEmail_throwsAndDoesNotSave() {
        UpdateUserRequest request = new UpdateUserRequest(
                null, null, "taken@example.com", null, null, null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> userService.update(userId, request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("user.already.exists.email");
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(userMapper, never()).updateEntityFromRequest(any(), any(User.class));
    }

    @Test
    void update_unknownUser_throwsNotFound() {
        UpdateUserRequest request = new UpdateUserRequest(
                null, null, null, null, null, null);
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.update(userId, request))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("user.not.found");
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void delete_existingUser_deletesFoundEntity() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(keycloakAdminClient.getAccessToken()).thenReturn("service-token");

        userService.delete(userId);

        verify(keycloakAdminClient).deleteUser("keycloak-user-id", "service-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).delete(captor.capture());
        assertThat(captor.getValue()).isSameAs(user);
    }

    @Test
    void delete_unknownUser_throwsNotFoundAndDoesNotDelete() {
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.delete(userId))
                .isInstanceOf(UserNotFoundException.class)
                .hasMessage("user.not.found");
        verify(userRepository, never()).delete(any(User.class));
    }
}
