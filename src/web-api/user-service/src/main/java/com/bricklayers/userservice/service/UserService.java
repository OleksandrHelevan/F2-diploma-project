package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserDto create(CreateUserRequest request);

    UserDto getById(UUID id);

    UserDto getByKeycloakId(String keycloakId);

    Page<UserDto> getUsers(int page, int size);

    UserDto update(UUID id, UpdateUserRequest request);

    void delete(UUID id);

    void deleteAll(List<UUID> ids);

    void setPassword(UUID userId, SetPasswordRequest request);
}