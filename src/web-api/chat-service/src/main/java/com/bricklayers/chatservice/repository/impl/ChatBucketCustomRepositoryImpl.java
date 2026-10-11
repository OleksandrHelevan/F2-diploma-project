package com.bricklayers.chatservice.repository.impl;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatBucketCustomRepositoryImpl
  @version  1.0.0 
  @since 06.10.2026 - 19.27
*/

import com.bricklayers.chatservice.entity.ChatBucket;
import com.bricklayers.chatservice.entity.ChatMessage;
import com.bricklayers.chatservice.entity.MessageStatus;
import com.bricklayers.chatservice.entity.MessageType;
import com.bricklayers.chatservice.repository.ChatBucketCustomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.bson.Document;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
public class ChatBucketCustomRepositoryImpl implements ChatBucketCustomRepository {

    private static final int MAX_BUCKET_SIZE = 50;

    private final MongoTemplate mongoTemplate;

    @Override
    public ChatBucket appendMessage(String chatId, String threadId, ChatMessage message) {
        Query query = Query.query(Criteria.where("chatId").is(chatId)
                        .and("threadId").is(threadId)
                        .and("count").lt(MAX_BUCKET_SIZE))
                .with(Sort.by(Sort.Direction.DESC, "endDate"))
                .limit(1);

        Update update = new Update()
                .push("messages", message)
                .inc("count", 1)
                .set("endDate", message.getTimestamp());

        ChatBucket updated = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), ChatBucket.class);

        if (updated != null) {
            return updated;
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(message);

        ChatBucket bucket = ChatBucket.builder()
                .chatId(chatId)
                .threadId(threadId)
                .count(1)
                .startDate(message.getTimestamp())
                .endDate(message.getTimestamp())
                .messages(messages)
                .build();

        return mongoTemplate.insert(bucket);
    }

    @Override
    public boolean registerThreadReply(String chatId, String parentMessageId, String senderId, LocalDateTime at) {
        Update update = new Update()
                .inc("messages.$[m].thread.replyCount", 1)
                .set("messages.$[m].thread.lastReplyAt", at)
                .addToSet("messages.$[m].thread.participantIds", senderId)
                .filterArray(Criteria.where("m.id").is(parentMessageId));

        return mongoTemplate.updateFirst(
                messageQuery(chatId, null, parentMessageId), update, ChatBucket.class
        ).getModifiedCount() > 0;
    }

    @Override
    public boolean editMessage(String chatId, String threadId, String messageId,
                               String newContent, LocalDateTime editedAt) {
        Update update = new Update()
                .set("messages.$[m].content", newContent)
                .set("messages.$[m].editedAt", editedAt)
                .filterArray(Criteria.where("m.id").is(messageId).and("m.deleted").is(false));

        return mongoTemplate.updateFirst(
                messageQuery(chatId, threadId, messageId), update, ChatBucket.class
        ).getModifiedCount() > 0;
    }

    @Override
    public boolean softDeleteMessage(String chatId, String threadId, String messageId) {
        Update update = new Update()
                .set("messages.$[m].deleted", true)
                .set("messages.$[m].content", null)
                .set("messages.$[m].attachments", List.of())
                .filterArray(Criteria.where("m.id").is(messageId));

        return mongoTemplate.updateFirst(
                messageQuery(chatId, threadId, messageId), update, ChatBucket.class
        ).getModifiedCount() > 0;
    }

    @Override
    public boolean setLike(String chatId, String threadId, String messageId, String userId, boolean liked) {
        Update update = new Update()
                .filterArray(Criteria.where("m.id").is(messageId));

        if (liked) {
            update.addToSet("messages.$[m].likedBy", userId);
        } else {
            update.pull("messages.$[m].likedBy", userId);
        }

        return mongoTemplate.updateFirst(
                messageQuery(chatId, threadId, messageId), update, ChatBucket.class
        ).getModifiedCount() > 0;
    }

    @Override
    public boolean updateStatus(String chatId, String threadId, String messageId, MessageStatus status) {
        Update update = new Update()
                .set("messages.$[m].status", status)
                .filterArray(Criteria.where("m.id").is(messageId));

        return mongoTemplate.updateFirst(
                messageQuery(chatId, threadId, messageId), update, ChatBucket.class
        ).getModifiedCount() > 0;
    }

    private Query messageQuery(String chatId, String threadId, String messageId) {
        return Query.query(Criteria.where("chatId").is(chatId)
                .and("threadId").is(threadId)
                .and("messages._id").is(messageId));
    }

    @Override
    public Optional<ChatMessage> findMessage(String chatId, String threadId, String messageId) {
        Query query = messageQuery(chatId, threadId, messageId);
        query.fields().elemMatch("messages", Criteria.where("_id").is(messageId));

        ChatBucket bucket = mongoTemplate.findOne(query, ChatBucket.class);
        if (bucket == null || bucket.getMessages() == null || bucket.getMessages().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(bucket.getMessages().getFirst());
    }

    @Override
    public long countUnread(String chatId, String userId, LocalDateTime after) {
        List<AggregationOperation> ops = List.of(
                Aggregation.match(Criteria.where("chatId").is(chatId)
                        .and("threadId").is(null)
                        .and("endDate").gt(after)),
                Aggregation.unwind("messages"),
                Aggregation.match(Criteria.where("messages.timestamp").gt(after)
                        .and("messages.senderId").ne(userId)
                        .and("messages.deleted").is(false)
                        .and("messages.type").ne(MessageType.SYSTEM.name())),
                Aggregation.count().as("total"));

        Document result = mongoTemplate.aggregate(
                Aggregation.newAggregation(ChatBucket.class, ops), Document.class
        ).getUniqueMappedResult();

        return result == null ? 0 : ((Number) result.get("total")).longValue();
    }

    @Override
    public void markDirectMessagesRead(String chatId, String recipientId) {
        Query query = Query.query(Criteria.where("chatId").is(chatId)
                .and("messages").elemMatch(Criteria.where("recipientId").is(recipientId)
                        .and("status").ne(MessageStatus.READ.name())));

        Update update = new Update()
                .set("messages.$[m].status", MessageStatus.READ.name())
                .filterArray(Criteria.where("m.recipientId").is(recipientId)
                        .and("m.status").ne(MessageStatus.READ.name()));

        mongoTemplate.updateMulti(query, update, ChatBucket.class);
    }
}