package com.bricklayers.chatservice.mapper;

/*
  @author   user
  @project   F2-diploma-project
  @class  ParticipantMapper
  @version  1.0.0 
  @since 06.10.2026 - 19.34
*/
import com.bricklayers.chatservice.dto.ParticipantResponse;
import com.bricklayers.chatservice.entity.ChatParticipant;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ParticipantMapper {

    ParticipantResponse toResponse(ChatParticipant participant);
}
