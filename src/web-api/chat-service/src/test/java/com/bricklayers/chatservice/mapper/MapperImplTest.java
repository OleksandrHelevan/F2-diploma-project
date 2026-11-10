package com.bricklayers.chatservice.mapper;

import com.bricklayers.chatservice.dto.AttachmentDto;
import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.dto.MessagePageResponse;
import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.dto.ParticipantResponse;
import com.bricklayers.chatservice.dto.SendMessageRequest;
import com.bricklayers.chatservice.dto.ThreadInfoResponse;
import com.bricklayers.chatservice.entity.Attachment;
import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatBucket;
import com.bricklayers.chatservice.entity.ChatMessage;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.entity.ChatType;
import com.bricklayers.chatservice.entity.MessageStatus;
import com.bricklayers.chatservice.entity.MessageType;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.entity.ThreadInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MapperImplTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 1, 12, 0);

    private AttachmentMapperImpl attachmentMapper;
    private ParticipantMapperImpl participantMapper;
    private ChatMapperImpl chatMapper;
    private MessageMapperImpl messageMapper;

    @BeforeEach
    void setUp() {
        attachmentMapper = new AttachmentMapperImpl();
        participantMapper = new ParticipantMapperImpl();
        chatMapper = new ChatMapperImpl();
        ReflectionTestUtils.setField(chatMapper, "participantMapper", participantMapper);
        messageMapper = new MessageMapperImpl();
        ReflectionTestUtils.setField(messageMapper, "attachmentMapper", attachmentMapper);
        ((MessageMapper) messageMapper).attachmentMapper = attachmentMapper;
    }

    @Test
    void attachmentMapperConvertsBothWays() {
        Attachment entity = new Attachment("http://f", "f.png", "image/png", 42);

        AttachmentDto dto = attachmentMapper.toDto(entity);
        assertThat(dto).isEqualTo(new AttachmentDto("http://f", "f.png", "image/png", 42));
        assertThat(attachmentMapper.toEntity(dto)).isEqualTo(entity);
        assertThat(attachmentMapper.toDtoList(List.of(entity))).containsExactly(dto);
        assertThat(attachmentMapper.toEntityList(List.of(dto))).containsExactly(entity);
    }

    @Test
    void attachmentMapperHandlesNulls() {
        assertThat(attachmentMapper.toDto(null)).isNull();
        assertThat(attachmentMapper.toEntity(null)).isNull();
        assertThat(attachmentMapper.toDtoList(null)).isNull();
        assertThat(attachmentMapper.toEntityList(null)).isNull();
    }

    @Test
    void participantMapperCopiesFields() {
        ChatParticipant participant = ChatParticipant.builder().userId("u1").role(ParticipantRole.FOREMAN)
                .joinedAt(NOW).leftAt(NOW.plusDays(1)).muted(true).build();

        assertThat(participantMapper.toResponse(participant))
                .isEqualTo(new ParticipantResponse("u1", ParticipantRole.FOREMAN, NOW, NOW.plusDays(1), true));
        assertThat(participantMapper.toResponse(null)).isNull();
    }

    @Test
    void chatMapperMapsChatWithParticipantsAndUnreadCount() {
        Chat chat = Chat.builder().id("chat-1").type(ChatType.CUSTOMER_FOREMAN).name("Project")
                .projectId("p1").brigadeId("br1").lastMessagePreview("hi").lastMessageAt(NOW).archived(true)
                .participants(List.of(ChatParticipant.builder().userId("u1").role(ParticipantRole.CUSTOMER).build()))
                .build();

        ChatResponse response = chatMapper.toResponse(chat, 7);

        assertThat(response.id()).isEqualTo("chat-1");
        assertThat(response.type()).isEqualTo(ChatType.CUSTOMER_FOREMAN);
        assertThat(response.name()).isEqualTo("Project");
        assertThat(response.projectId()).isEqualTo("p1");
        assertThat(response.brigadeId()).isEqualTo("br1");
        assertThat(response.lastMessagePreview()).isEqualTo("hi");
        assertThat(response.lastMessageAt()).isEqualTo(NOW);
        assertThat(response.unreadCount()).isEqualTo(7);
        assertThat(response.archived()).isTrue();
        assertThat(response.participants()).extracting(ParticipantResponse::userId).containsExactly("u1");
    }

    @Test
    void chatMapperHandlesNullChatAndNullParticipants() {
        assertThat(chatMapper.toResponse(null, 1)).isNull();
        Chat chat = Chat.builder().id("c").participants(null).build();
        assertThat(chatMapper.toResponse(chat, 0).participants()).isNull();
    }

    @Test
    void messageMapperMapsMessageWithLikesThreadAndAttachments() {
        ChatMessage message = ChatMessage.builder().id("m1").senderId("u1").senderName("User").type(MessageType.TEXT)
                .content("hello").timestamp(NOW).editedAt(NOW.plusMinutes(1)).recipientId("u2")
                .status(MessageStatus.DELIVERED).likedBy(new HashSet<>(Set.of("u2", "u3")))
                .attachments(List.of(new Attachment("http://f", "f", "text/plain", 1)))
                .thread(ThreadInfo.builder().replyCount(3).lastReplyAt(NOW).participantIds(Set.of("u2")).build())
                .build();

        MessageResponse response = messageMapper.toResponse(message, "u2");

        assertThat(response.id()).isEqualTo("m1");
        assertThat(response.senderId()).isEqualTo("u1");
        assertThat(response.senderName()).isEqualTo("User");
        assertThat(response.content()).isEqualTo("hello");
        assertThat(response.editedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(response.recipientId()).isEqualTo("u2");
        assertThat(response.status()).isEqualTo(MessageStatus.DELIVERED);
        assertThat(response.likesCount()).isEqualTo(2);
        assertThat(response.likedByMe()).isTrue();
        assertThat(response.attachments()).containsExactly(new AttachmentDto("http://f", "f", "text/plain", 1));
        assertThat(response.thread()).isEqualTo(new ThreadInfoResponse(3, NOW, Set.of("u2")));
        assertThat(messageMapper.toResponse(message, "u9").likedByMe()).isFalse();
    }

    @Test
    void messageMapperHandlesNullsAndMissingLikes() {
        assertThat(messageMapper.toResponse((ChatMessage) null, "u1")).isNull();
        assertThat(messageMapper.toResponse((ThreadInfo) null)).isNull();
        ChatMessage message = ChatMessage.builder().id("m1").likedBy(null).build();
        MessageResponse response = messageMapper.toResponse(message, "u1");
        assertThat(response.likesCount()).isZero();
        assertThat(response.likedByMe()).isFalse();
        assertThat(response.thread()).isNull();
        ThreadInfo info = ThreadInfo.builder().participantIds(null).build();
        assertThat(messageMapper.toResponse(info).participantIds()).isNull();
    }

    @Test
    void toResponseListKeepsOrder() {
        List<MessageResponse> responses = messageMapper.toResponseList(List.of(
                ChatMessage.builder().id("a").build(), ChatMessage.builder().id("b").build()), "u1");

        assertThat(responses).extracting(MessageResponse::id).containsExactly("a", "b");
    }

    @Test
    void toPageReversesMessagesWithinEachBucket() {
        ChatBucket newest = ChatBucket.builder().messages(new ArrayList<>(List.of(
                ChatMessage.builder().id("3").build(), ChatMessage.builder().id("4").build()))).build();
        ChatBucket older = ChatBucket.builder().messages(new ArrayList<>(List.of(
                ChatMessage.builder().id("1").build(), ChatMessage.builder().id("2").build()))).build();

        MessagePageResponse page = messageMapper.toPage(
                new SliceImpl<>(List.of(newest, older), PageRequest.of(1, 2), true), "u1");

        assertThat(page.messages()).extracting(MessageResponse::id).containsExactly("4", "3", "2", "1");
        assertThat(page.hasMore()).isTrue();
        assertThat(page.page()).isEqualTo(1);
        assertThat(newest.getMessages()).extracting(ChatMessage::getId).containsExactly("3", "4");
    }

    @Test
    void toNewMessageForDirectRecipientWithAttachments() {
        SendMessageRequest request = new SendMessageRequest("hi", null, "u2",
                List.of(new AttachmentDto("http://f", "f", "image/png", 5)));

        ChatMessage message = messageMapper.toNewMessage(request, "u1", "User", NOW);

        assertThat(message.getId()).isNotBlank();
        assertThat(message.getSenderId()).isEqualTo("u1");
        assertThat(message.getSenderName()).isEqualTo("User");
        assertThat(message.getType()).isEqualTo(MessageType.TEXT);
        assertThat(message.getContent()).isEqualTo("hi");
        assertThat(message.getTimestamp()).isEqualTo(NOW);
        assertThat(message.getRecipientId()).isEqualTo("u2");
        assertThat(message.getStatus()).isEqualTo(MessageStatus.RECEIVED);
        assertThat(message.getAttachments()).containsExactly(new Attachment("http://f", "f", "image/png", 5));
    }

    @Test
    void toNewMessageWithoutRecipientOrAttachments() {
        ChatMessage message = messageMapper.toNewMessage(new SendMessageRequest("hi", null, null, null), "u1", "User", NOW);

        assertThat(message.getStatus()).isNull();
        assertThat(message.getAttachments()).isEmpty();
    }

    @Test
    void toSystemMessageBuildsSystemTypedMessage() {
        ChatMessage message = messageMapper.toSystemMessage("info", NOW);

        assertThat(message.getType()).isEqualTo(MessageType.SYSTEM);
        assertThat(message.getContent()).isEqualTo("info");
        assertThat(message.getTimestamp()).isEqualTo(NOW);
        assertThat(message.getSenderId()).isNull();
    }
}
