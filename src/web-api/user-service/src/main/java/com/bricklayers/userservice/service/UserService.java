package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;

import java.util.List;
import java.util.UUID;

public interface UserService {

    UserDto create(CreateUserRequest request);

    UserDto getById(UUID id);

    List<UserDto> getAll();

    UserDto update(UUID id, UpdateUserRequest request);

    void delete(UUID id);
}