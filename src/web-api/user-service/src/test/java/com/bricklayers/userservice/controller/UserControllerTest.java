package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private UUID userId;
    private UserDto userDto;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
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
    void create_returnsCreatedUserAndLocation() {
        CreateUserRequest request = new CreateUserRequest(
                "builder", "builder@example.com", "Ivan", "Builder", Role.BUILDER, null);
        when(userService.create(request)).thenReturn(userDto);

        var response = userController.create(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getLocation()).hasToString("/api/v1/users/" + userId);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(userService).create(request);
    }

    @Test
    void getById_returnsUser() {
        when(userService.getById(userId)).thenReturn(userDto);

        var response = userController.getById(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(userService).getById(userId);
    }

    @Test
    void getAll_returnsUsers() {
        List<UserDto> users = List.of(userDto);
        when(userService.getAll()).thenReturn(users);

        var response = userController.getAll();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(userDto);
        verify(userService).getAll();
    }

    @Test
    void update_returnsUpdatedUser() {
        UpdateUserRequest request = new UpdateUserRequest(
                "Ivan", "Builder", "updated@example.com", Role.BUILDER, null, true);
        when(userService.update(userId, request)).thenReturn(userDto);

        var response = userController.update(userId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(userDto);
        verify(userService).update(userId, request);
    }

    @Test
    void delete_delegatesToService() {
        userController.delete(userId);

        verify(userService).delete(userId);
    }
}