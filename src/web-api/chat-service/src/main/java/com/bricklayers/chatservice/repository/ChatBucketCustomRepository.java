package com.bricklayers.chatservice.repository;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatBucketCustomRepository
  @version  1.0.0 
  @since 06.10.2026 - 19.27
*/

import com.bricklayers.chatservice.entity.ChatBucket;
import com.bricklayers.chatservice.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ChatBucketCustomRepository {

    ChatBucket appendMessage(String chatId, String threadId, ChatMessage message);

    boolean registerThreadReply(String chatId, String parentMessageId, String senderId, LocalDateTime at);

    boolean editMessage(String chatId, String threadId, String messageId, String newContent, LocalDateTime editedAt);

    boolean softDeleteMessage(String chatId, String threadId, String messageId);

    boolean setLike(String chatId, String threadId, String messageId, String userId, boolean liked);

    boolean updateStatus(String chatId, String threadId, String messageId,
                         com.bricklayers.chatservice.entity.MessageStatus status);
    Optional<ChatMessage> findMessage(String chatId, String threadId, String messageId);

    long countUnread(String chatId, String userId, LocalDateTime after);

    void markDirectMessagesRead(String chatId, String recipientId);
}