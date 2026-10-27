package com.bricklayers.userservice.service;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import org.springframework.data.domain.Page;

public interface AdminAuditService {

    Page<AdminAuditLogDto> getAuditLogs(int page, int size);
}
