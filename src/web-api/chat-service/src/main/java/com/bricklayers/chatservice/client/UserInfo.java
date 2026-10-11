package com.bricklayers.chatservice.client;

/*
  @author   user
  @project   F2-diploma-project
  @class  UserDirectory
  @version  1.0.0 
  @since 06.10.2026 - 22.09
*/
import com.bricklayers.chatservice.entity.ParticipantRole;

public record UserInfo(
        String id,
        String username,
        String firstName,
        String lastName,
        String companyId,
        ParticipantRole role
) {}