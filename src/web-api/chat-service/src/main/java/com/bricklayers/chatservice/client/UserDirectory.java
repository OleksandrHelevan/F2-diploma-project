package com.bricklayers.chatservice.client;

/*
  @author   user
  @project   F2-diploma-project
  @class  UserDirectory
  @version  1.0.0 
  @since 06.10.2026 - 22.10
*/

import com.bricklayers.common.dto.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "user-service",
        url = "${user-service.url:http://localhost:8081}"
)
public interface UserDirectory {
    @GetMapping("/api/v1/users/{userId}")
    UserInfo getUser(@PathVariable String userId);

    @GetMapping("/api/v1/me/by-keycloak-id/{keycloakId}")
    UserInfo getByKeycloakId(
            @PathVariable("keycloakId") String keycloakId,
            @RequestHeader("Authorization") String authorization);
}