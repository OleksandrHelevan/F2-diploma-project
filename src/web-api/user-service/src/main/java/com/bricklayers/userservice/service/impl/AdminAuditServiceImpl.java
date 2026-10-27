package com.bricklayers.userservice.service.impl;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import com.bricklayers.userservice.mapper.AdminAuditLogMapper;
import com.bricklayers.userservice.repository.AdminAuditLogRepository;
import com.bricklayers.userservice.service.AdminAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuditServiceImpl implements AdminAuditService {

    private final AdminAuditLogRepository auditLogRepository;
    private final AdminAuditLogMapper auditLogMapper;

    @Override
    public Page<AdminAuditLogDto> getAuditLogs(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        return auditLogRepository
                .findAll(pageable)
                .map(auditLogMapper::toDto);
    }
}
