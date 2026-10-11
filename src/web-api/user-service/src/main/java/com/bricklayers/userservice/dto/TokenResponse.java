package com.bricklayers.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/*
  @author   user
  @project   F2-diploma-project
  @class  TokenResponse
  @version  1.0.0 
  @since 09.10.2026 - 00.01
*/
public record TokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn) {
}
