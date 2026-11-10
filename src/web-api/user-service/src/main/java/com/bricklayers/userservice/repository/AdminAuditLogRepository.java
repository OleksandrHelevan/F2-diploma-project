package com.bricklayers.userservice.repository;

import com.bricklayers.userservice.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, UUID> {

    long countByTarget(String target);

    List<AdminAuditLog> findTop5ByTargetOrderByCreatedAtDesc(String target);
}
