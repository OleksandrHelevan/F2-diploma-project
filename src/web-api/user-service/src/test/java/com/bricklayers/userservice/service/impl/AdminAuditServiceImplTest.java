package com.bricklayers.userservice.service.impl;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import com.bricklayers.userservice.entity.AdminAuditLog;
import com.bricklayers.userservice.entity.AuditOutcome;
import com.bricklayers.userservice.mapper.AdminAuditLogMapper;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAuditServiceImplTest {

    @Mock
    private AdminAuditLogRepository auditLogRepository;

    @Mock
    private AdminAuditLogMapper auditLogMapper;

    @InjectMocks
    private AdminAuditServiceImpl adminAuditService;

    @Test
    void getAuditLogs_returnsNewestFirst() {
        AdminAuditLog log = AdminAuditLog.builder().id(UUID.randomUUID()).action("USER_CREATE")
                .actor("system").outcome(AuditOutcome.SUCCESS).createdAt(LocalDateTime.now()).build();
        AdminAuditLogDto dto = new AdminAuditLogDto(log.getId(), "USER_CREATE", "system",
                null, AuditOutcome.SUCCESS, null, log.getCreatedAt());
        Pageable expected = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "createdAt"));
        when(auditLogRepository.findAll(expected)).thenReturn(new PageImpl<>(List.of(log), expected, 6));
        when(auditLogMapper.toDto(log)).thenReturn(dto);

        var result = adminAuditService.getAuditLogs(1, 5);

        assertThat(result.getContent()).containsExactly(dto);
        assertThat(result.getTotalElements()).isEqualTo(6);
    }
}
