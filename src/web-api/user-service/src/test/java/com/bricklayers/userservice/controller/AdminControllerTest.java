package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private UserRepository userRepository;

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
}