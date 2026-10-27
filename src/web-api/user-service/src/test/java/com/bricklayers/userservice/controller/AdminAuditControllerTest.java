package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import com.bricklayers.userservice.entity.AuditOutcome;
import com.bricklayers.userservice.service.AdminAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditControllerTest {

    @Mock
    private AdminAuditService adminAuditService;

    @InjectMocks
    private AdminAuditController adminAuditController;

    @Test
    void getAuditLogs_returnsPageFromService() {
        AdminAuditLogDto record = new AdminAuditLogDto(UUID.randomUUID(), "USER_DELETE", "root(kc-id)",
                UUID.randomUUID().toString(), AuditOutcome.SUCCESS, null, LocalDateTime.now());
        Page<AdminAuditLogDto> page = new PageImpl<>(List.of(record), PageRequest.of(0, 20), 1);
        when(adminAuditService.getAuditLogs(0, 20)).thenReturn(page);

        var response = adminAuditController.getAuditLogs(0, 20);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(page);
        verify(adminAuditService).getAuditLogs(0, 20);
    }
}
