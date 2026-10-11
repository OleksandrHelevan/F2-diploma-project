package com.bricklayers.chatservice.repository;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatCustomRepository
  @version  1.0.0 
  @since 06.10.2026 - 19.25
*/

import com.bricklayers.chatservice.entity.ChatParticipant;

import java.time.LocalDateTime;

public interface ChatCustomRepository {

    void updateLastMessage(String chatId, String preview, LocalDateTime at);

    void markAsRead(String chatId, String userId, LocalDateTime readAt);

    void addParticipant(String chatId, ChatParticipant participant);

    void markParticipantLeft(String chatId, String userId, LocalDateTime leftAt);

    void setBrigade(String chatId, String brigadeId);

    void setArchived(String chatId, boolean archived);
}