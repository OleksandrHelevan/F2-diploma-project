package com.bricklayers.userservice.controller;

import com.bricklayers.userservice.db.ReplicationStatusService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/db")
@SecurityRequirement(name = "keycloak", scopes = {"openid", "profile", "email"})
@RequiredArgsConstructor
public class ReplicationStatusController {

    private final ReplicationStatusService replicationStatusService;

    @GetMapping("/replication")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public Map<String, Object> replication() {
        return replicationStatusService.status();
    }
}
