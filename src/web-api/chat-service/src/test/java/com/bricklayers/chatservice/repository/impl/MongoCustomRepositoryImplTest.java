package com.bricklayers.chatservice.repository.impl;

import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatBucket;
import com.bricklayers.chatservice.entity.ChatMessage;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.entity.MessageStatus;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.TypedAggregation;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MongoCustomRepositoryImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 4, 1, 9, 0);

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void appendMessageReturnsUpdatedBucketWhenSpaceAvailable() {
        ChatBucketCustomRepositoryImpl repository = new ChatBucketCustomRepositoryImpl(mongoTemplate);
        ChatMessage message = ChatMessage.builder().id("m1").timestamp(NOW).build();
        ChatBucket existing = ChatBucket.builder().id("b1").build();
        when(mongoTemplate.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(ChatBucket.class))).thenReturn(existing);

        assertThat(repository.appendMessage("chat-1", null, message)).isSameAs(existing);

        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).findAndModify(query.capture(), update.capture(), any(FindAndModifyOptions.class),
                eq(ChatBucket.class));
        assertThat(query.getValue().getQueryObject().get("chatId")).isEqualTo("chat-1");
        assertThat(query.getValue().getLimit()).isEqualTo(1);
        assertThat(update.getValue().getUpdateObject().get("$inc", Document.class).get("count")).isEqualTo(1);
        verify(mongoTemplate, never()).insert(any(ChatBucket.class));
    }

    @Test
    void appendMessageCreatesNewBucketWhenAllAreFull() {
        ChatBucketCustomRepositoryImpl repository = new ChatBucketCustomRepositoryImpl(mongoTemplate);
        ChatMessage message = ChatMessage.builder().id("m1").timestamp(NOW).build();
        when(mongoTemplate.insert(any(ChatBucket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ChatBucket bucket = repository.appendMessage("chat-1", "t1", message);

        assertThat(bucket.getChatId()).isEqualTo("chat-1");
        assertThat(bucket.getThreadId()).isEqualTo("t1");
        assertThat(bucket.getCount()).isEqualTo(1);
        assertThat(bucket.getStartDate()).isEqualTo(NOW);
        assertThat(bucket.getEndDate()).isEqualTo(NOW);
        assertThat(bucket.getMessages()).containsExactly(message);
    }

    @Test
    void messageUpdatesReportWhetherDocumentWasModified() {
        ChatBucketCustomRepositoryImpl repository = new ChatBucketCustomRepositoryImpl(mongoTemplate);
        when(mongoTemplate.updateFirst(any(Query.class), any(Update.class), eq(ChatBucket.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(1, 0L, null),
                        UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(1, 1L, null),
                        UpdateResult.acknowledged(1, 1L, null), UpdateResult.acknowledged(1, 0L, null));

        assertThat(repository.registerThreadReply("chat-1", "parent", "u1", NOW)).isTrue();
        assertThat(repository.editMessage("chat-1", null, "m1", "new", NOW)).isFalse();
        assertThat(repository.softDeleteMessage("chat-1", null, "m1")).isTrue();
        assertThat(repository.setLike("chat-1", null, "m1", "u1", true)).isTrue();
        assertThat(repository.setLike("chat-1", null, "m1", "u1", false)).isTrue();
        assertThat(repository.updateStatus("chat-1", "t1", "m1", MessageStatus.DELIVERED)).isFalse();

        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.times(6)).updateFirst(any(Query.class), updates.capture(), eq(ChatBucket.class));
        List<Update> captured = updates.getAllValues();
        assertThat(captured.get(3).getUpdateObject()).containsKey("$addToSet");
        assertThat(captured.get(4).getUpdateObject()).containsKey("$pull");
        assertThat(captured.get(5).getUpdateObject().get("$set", Document.class).get("messages.$[m].status"))
                .isEqualTo(MessageStatus.DELIVERED);
    }

    @Test
    void findMessageReturnsFirstProjectedMessage() {
        ChatBucketCustomRepositoryImpl repository = new ChatBucketCustomRepositoryImpl(mongoTemplate);
        ChatMessage message = ChatMessage.builder().id("m1").build();
        when(mongoTemplate.findOne(any(Query.class), eq(ChatBucket.class)))
                .thenReturn(ChatBucket.builder().messages(new ArrayList<>(List.of(message))).build())
                .thenReturn(null)
                .thenReturn(ChatBucket.builder().messages(null).build())
                .thenReturn(ChatBucket.builder().build());

        assertThat(repository.findMessage("chat-1", null, "m1")).contains(message);
        assertThat(repository.findMessage("chat-1", null, "m1")).isEmpty();
        assertThat(repository.findMessage("chat-1", null, "m1")).isEmpty();
        assertThat(repository.findMessage("chat-1", null, "m1")).isEmpty();
    }

    @Test
    void countUnreadReadsAggregatedTotal() {
        ChatBucketCustomRepositoryImpl repository = new ChatBucketCustomRepositoryImpl(mongoTemplate);
        when(mongoTemplate.aggregate(any(TypedAggregation.class), eq(Document.class)))
                .thenReturn(new AggregationResults<>(List.of(new Document("total", 3)), new Document()))
                .thenReturn(new AggregationResults<>(List.of(), new Document()));

        assertThat(repository.countUnread("chat-1", "u1", NOW)).isEqualTo(3L);
        assertThat(repository.countUnread("chat-1", "u1", NOW)).isZero();
    }

    @Test
    void markDirectMessagesReadUpdatesAllMatchingBuckets() {
        ChatBucketCustomRepositoryImpl repository = new ChatBucketCustomRepositoryImpl(mongoTemplate);

        repository.markDirectMessagesRead("chat-1", "u2");

        ArgumentCaptor<Update> update = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate).updateMulti(any(Query.class), update.capture(), eq(ChatBucket.class));
        assertThat(update.getValue().getUpdateObject().get("$set", Document.class).get("messages.$[m].status"))
                .isEqualTo("READ");
    }

    @Test
    void chatRepositoryUpdatesTargetChatDocument() {
        ChatCustomRepositoryImpl repository = new ChatCustomRepositoryImpl(mongoTemplate);
        ChatParticipant participant = ChatParticipant.builder().userId("u1").role(ParticipantRole.BUILDER).build();

        repository.updateLastMessage("chat-1", "hi", NOW);
        repository.markAsRead("chat-1", "u1", NOW);
        repository.addParticipant("chat-1", participant);
        repository.markParticipantLeft("chat-1", "u1", NOW);
        repository.setBrigade("chat-1", "br1");
        repository.setArchived("chat-1", true);

        ArgumentCaptor<Query> queries = ArgumentCaptor.forClass(Query.class);
        ArgumentCaptor<Update> updates = ArgumentCaptor.forClass(Update.class);
        verify(mongoTemplate, org.mockito.Mockito.times(6)).updateFirst(queries.capture(), updates.capture(), eq(Chat.class));
        assertThat(queries.getAllValues()).allSatisfy(q -> assertThat(q.getQueryObject().get("_id")).isEqualTo("chat-1"));
        List<Update> captured = updates.getAllValues();
        assertThat(captured.get(0).getUpdateObject().get("$set", Document.class))
                .containsEntry("lastMessagePreview", "hi").containsEntry("lastMessageAt", NOW);
        assertThat(captured.get(1).getUpdateObject().get("$set", Document.class))
                .containsEntry("participants.$[p].lastReadAt", NOW);
        assertThat(captured.get(2).getUpdateObject().get("$push", Document.class))
                .containsEntry("participants", participant);
        assertThat(captured.get(3).getUpdateObject().get("$set", Document.class))
                .containsEntry("participants.$[p].leftAt", NOW);
        assertThat(captured.get(4).getUpdateObject().get("$set", Document.class)).containsEntry("brigadeId", "br1");
        assertThat(captured.get(5).getUpdateObject().get("$set", Document.class)).containsEntry("archived", true);
    }
}
