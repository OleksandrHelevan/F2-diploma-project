package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.RegisterRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserAlreadyExistsException;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserDto register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException("user.already.exists.username", request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("user.already.exists.email", request.email());
        }

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .firstName(request.firstName())
                .lastName(request.lastName())
                .role(Role.BUILDER)
                .active(true)
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        return userMapper.toDto(userRepository.save(user));
    }
}
