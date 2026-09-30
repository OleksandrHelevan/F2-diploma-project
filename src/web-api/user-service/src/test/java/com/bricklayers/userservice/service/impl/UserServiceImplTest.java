package com.bricklayers.userservice.service.impl;

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserAlreadyExistsException;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

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
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.create(request);

        assertThat(result).isEqualTo(userDto);
        verify(userRepository).save(user);
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
        verify(userRepository, never()).save(any(User.class));
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
        verify(userRepository, never()).save(any(User.class));
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
    void getAll_usersExist_returnsMappedDtos() {
        User secondUser = User.builder()
                .id(UUID.randomUUID())
                .username("foreman")
                .email("foreman@example.com")
                .role(Role.FOREMAN)
                .active(true)
                .build();
        UserDto secondDto = new UserDto(
                secondUser.getId(), "foreman", "foreman@example.com", null, null,
                Role.FOREMAN, null, true, null, null);
        when(userRepository.findAll()).thenReturn(List.of(user, secondUser));
        when(userMapper.toDto(user)).thenReturn(userDto);
        when(userMapper.toDto(secondUser)).thenReturn(secondDto);

        List<UserDto> result = userService.getAll();

        assertThat(result).containsExactly(userDto, secondDto);
        verify(userRepository).findAll();
        verify(userMapper).toDto(user);
        verify(userMapper).toDto(secondUser);
    }

    @Test
    void getAll_noUsers_returnsEmptyList() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<UserDto> result = userService.getAll();

        assertThat(result).isEmpty();
        verify(userRepository).findAll();
    }

    @Test
    void update_changedEmail_savesUpdatedEntityAndReturnsDto() {
        UpdateUserRequest request = new UpdateUserRequest(
                "Ivan", "Builder", "updated@example.com", Role.FOREMAN, null, true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(userDto);

        UserDto result = userService.update(userId, request);

        assertThat(result).isEqualTo(userDto);
        verify(userMapper).updateEntityFromRequest(request, user);
        verify(userRepository).save(user);
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
        verify(userRepository, never()).save(any(User.class));
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
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void delete_existingUser_deletesFoundEntity() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        userService.delete(userId);

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
