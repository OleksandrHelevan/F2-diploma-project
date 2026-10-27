package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.dto.AdminAuditLogDto;
import com.bricklayers.userservice.service.AdminAuditService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
@SecurityRequirement(name = "keycloak", scopes = {"openid", "profile", "email"})
@RequiredArgsConstructor
public class AdminAuditController {

    private final AdminAuditService adminAuditService;

    @GetMapping
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<Page<AdminAuditLogDto>> getAuditLogs(@RequestParam int page, @RequestParam int size) {
        return ResponseEntity.ok(adminAuditService.getAuditLogs(page, size));
    }
}
