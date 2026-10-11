package com.bricklayers.chatservice.mapper;

/*
  @author   user
  @project   F2-diploma-project
  @class  MessageMapper
  @version  1.0.0 
  @since 06.10.2026 - 19.35
*/

import com.bricklayers.chatservice.dto.SendMessageRequest;
import com.bricklayers.chatservice.dto.MessagePageResponse;
import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.dto.ThreadInfoResponse;
import com.bricklayers.chatservice.entity.*;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Slice;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring", uses = AttachmentMapper.class)
public abstract class MessageMapper {

    @Autowired
    protected AttachmentMapper attachmentMapper;


    @Mapping(target = "likesCount", expression = "java(message.getLikedBy() == null ? 0 : message.getLikedBy().size())")
    @Mapping(target = "likedByMe",
            expression = "java(message.getLikedBy() != null && message.getLikedBy().contains(currentUserId))")
    public abstract MessageResponse toResponse(ChatMessage message, @Context String currentUserId);

    public abstract ThreadInfoResponse toResponse(ThreadInfo info);

    public List<MessageResponse> toResponseList(List<ChatMessage> messages, String currentUserId) {
        List<MessageResponse> result = new ArrayList<>(messages.size());
        for (ChatMessage m : messages) {
            result.add(toResponse(m, currentUserId));
        }
        return result;
    }


    public MessagePageResponse toPage(Slice<ChatBucket> buckets, String currentUserId) {
        List<MessageResponse> result = new ArrayList<>();
        for (ChatBucket bucket : buckets.getContent()) {
            List<ChatMessage> copy = new ArrayList<>(bucket.getMessages());
            Collections.reverse(copy);
            for (ChatMessage m : copy) {
                result.add(toResponse(m, currentUserId));
            }
        }
        return new MessagePageResponse(result, buckets.hasNext(), buckets.getNumber());
    }


    public ChatMessage toNewMessage(SendMessageRequest request,
                                    String senderId,
                                    String senderName,
                                    LocalDateTime now) {
        return ChatMessage.builder()
                .senderId(senderId)
                .senderName(senderName)
                .type(MessageType.TEXT)
                .content(request.content())
                .timestamp(now)
                .recipientId(request.recipientId())
                .status(request.recipientId() != null ? MessageStatus.RECEIVED : null)
                .attachments(request.attachments() == null
                        ? new ArrayList<>()
                        : attachmentMapper.toEntityList(request.attachments()))
                .build();
    }

    public ChatMessage toSystemMessage(String content, LocalDateTime now) {
        return ChatMessage.builder()
                .type(MessageType.SYSTEM)
                .content(content)
                .timestamp(now)
                .build();
    }
}
