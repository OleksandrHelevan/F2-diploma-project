package com.bricklayers.chatservice.repository.impl;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatCustomRepositoryImpl
  @version  1.0.0 
  @since 06.10.2026 - 19.26
*/

import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.repository.ChatCustomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;

@RequiredArgsConstructor
public class ChatCustomRepositoryImpl implements ChatCustomRepository {

    private final MongoTemplate mongoTemplate;

    @Override
    public void updateLastMessage(String chatId, String preview, LocalDateTime at) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(chatId)),
                new Update().set("lastMessagePreview", preview).set("lastMessageAt", at),
                Chat.class);
    }

    @Override
    public void markAsRead(String chatId, String userId, LocalDateTime readAt) {
        Update update = new Update()
                .set("participants.$[p].lastReadAt", readAt)
                .filterArray(Criteria.where("p.userId").is(userId).and("p.leftAt").is(null));

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(chatId)), update, Chat.class);
    }

    @Override
    public void addParticipant(String chatId, ChatParticipant participant) {
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(chatId)),
                new Update().push("participants", participant),
                Chat.class);
    }

    @Override
    public void markParticipantLeft(String chatId, String userId, LocalDateTime leftAt) {
        Update update = new Update()
                .set("participants.$[p].leftAt", leftAt)
                .filterArray(Criteria.where("p.userId").is(userId).and("p.leftAt").is(null));

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("_id").is(chatId)), update, Chat.class);
    }

    @Override
    public void setBrigade(String chatId, String brigadeId) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(chatId)),
                new Update().set("brigadeId", brigadeId), Chat.class);
    }

    @Override
    public void setArchived(String chatId, boolean archived) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(chatId)),
                new Update().set("archived", archived), Chat.class);
    }
}