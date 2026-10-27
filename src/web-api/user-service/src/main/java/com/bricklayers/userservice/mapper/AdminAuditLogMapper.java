package com.bricklayers.userservice.mapper;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import com.bricklayers.userservice.entity.AdminAuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AdminAuditLogMapper {

    AdminAuditLogDto toDto(AdminAuditLog log);
}
