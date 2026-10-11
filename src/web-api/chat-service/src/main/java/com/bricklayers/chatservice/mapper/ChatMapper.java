package com.bricklayers.chatservice.mapper;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatMapper
  @version  1.0.0 
  @since 06.10.2026 - 19.34
*/
import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.entity.Chat;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = ParticipantMapper.class)
public interface ChatMapper {

    ChatResponse toResponse(Chat chat, int unreadCount);
}
