package com.bricklayers.userservice.dto;

import jakarta.validation.constraints.NotBlank;

/*
  @author   user
  @project   F2-diploma-project
  @class  LoginRequest
  @version  1.0.0 
  @since 09.10.2026 - 00.01
*/
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password) { }


