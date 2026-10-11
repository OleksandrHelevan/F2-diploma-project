package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminController adminController;

    @Test
    void stats_returnsUserCountAndAdminMessage() {
        when(userRepository.count()).thenReturn(7L);

        var stats = adminController.stats();

        assertThat(stats).containsEntry("userCount", 7L);
        assertThat(stats).containsEntry("message", "Admin-only statistics");
        verify(userRepository).count();
    }

    @Test
    void setPasswordStoresOnlyEncodedPassword() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .username("builder")
                .email("builder@example.com")
                .role(Role.BUILDER)
                .active(true)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password-123")).thenReturn("bcrypt-hash");

        adminController.setPassword(userId, new SetPasswordRequest("new-password-123"));

        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
        verify(userRepository).save(user);
    }

    @Test
    void setPasswordUnknownUserReturnsNotFound() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminController.setPassword(
                userId, new SetPasswordRequest("new-password-123")))
                .isInstanceOf(UserNotFoundException.class);
    }
}