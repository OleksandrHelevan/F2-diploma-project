package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.RegisterRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserAlreadyExistsException;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private RegistrationService registrationService;

    @Test
    void registerCreatesBuilderAndStoresEncodedPasswordLocally() {
        RegisterRequest request = new RegisterRequest(
                "newbuilder", "newbuilder@example.com", "secure-pass-123", "New", "Builder");
        UUID localUserId = UUID.randomUUID();
        UserDto expected = new UserDto(
                localUserId, request.username(), request.email(), "New", "Builder",
                Role.BUILDER, null, true, null, null);
        when(userRepository.existsByUsername(request.username())).thenReturn(false);
        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("bcrypt-hash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(localUserId);
            return user;
        });
        when(userMapper.toDto(any(User.class))).thenReturn(expected);

        UserDto result = registrationService.register(request);

        assertThat(result).isEqualTo(expected);
        var userCaptor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getId()).isEqualTo(localUserId);
        assertThat(userCaptor.getValue().getRole()).isEqualTo(Role.BUILDER);
        assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("bcrypt-hash");
    }

    @Test
    void registerRejectsAnExistingLocalUsernameWithoutChangingPassword() {
        RegisterRequest request = new RegisterRequest(
                "existing", "new@example.com", "secure-pass-123", null, null);
        when(userRepository.existsByUsername(request.username())).thenReturn(true);

        assertThatThrownBy(() -> registrationService.register(request))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessage("user.already.exists.username");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any(User.class));
    }
}
