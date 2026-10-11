package com.bricklayers.chatservice.dto;

/*
  @author   user
  @project   F2-diploma-project
  @class  EditMessageRequest
  @version  1.0.0 
  @since 06.10.2026 - 19.31
*/

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditMessageRequest(
        @NotBlank @Size(max = 4000) String content
) {}