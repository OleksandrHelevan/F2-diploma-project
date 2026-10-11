package com.bricklayers.chatservice.mapper;

/*
  @author   user
  @project   F2-diploma-project
  @class  AttachmentMapper
  @version  1.0.0 
  @since 06.10.2026 - 19.34
*/

import com.bricklayers.chatservice.dto.AttachmentDto;
import com.bricklayers.chatservice.entity.Attachment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AttachmentMapper {

    AttachmentDto toDto(Attachment attachment);

    Attachment toEntity(AttachmentDto dto);

    List<AttachmentDto> toDtoList(List<Attachment> attachments);

    List<Attachment> toEntityList(List<AttachmentDto> dtos);
}
