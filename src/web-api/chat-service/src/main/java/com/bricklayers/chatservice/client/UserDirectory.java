package com.bricklayers.chatservice.client;

/*
  @author   user
  @project   F2-diploma-project
  @class  UserDirectory
  @version  1.0.0 
  @since 06.10.2026 - 22.10
*/

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
        name = "user-service",
        url = "${user-service.url:http://localhost:8081}",
        path = "/api/v1/users"
)
public interface UserDirectory {
    @GetMapping("/{userId}")
    UserInfo getUser(@PathVariable("userId") String userId);
}