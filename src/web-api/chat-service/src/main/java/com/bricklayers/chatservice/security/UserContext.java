package com.bricklayers.chatservice.security;

/*
  @author   user
  @project   F2-diploma-project
  @class  UserContext
  @version  1.0.0 
  @since 06.10.2026 - 22.09
*/

import com.bricklayers.chatservice.entity.ParticipantRole;

public record UserContext(String userId, String name, String companyId, ParticipantRole role) {}