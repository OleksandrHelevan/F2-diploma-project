package com.bricklayers.chatservice.service;

import com.bricklayers.chatservice.dto.AttachmentDto;
import com.bricklayers.chatservice.dto.EditMessageRequest;
import com.bricklayers.chatservice.dto.MessagePageResponse;
import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.dto.SendMessageRequest;
import com.bricklayers.chatservice.dto.ThreadInfoResponse;
import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatBucket;
import com.bricklayers.chatservice.entity.ChatEventType;
import com.bricklayers.chatservice.entity.ChatMessage;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.entity.ChatType;
import com.bricklayers.chatservice.entity.MessageStatus;
import com.bricklayers.chatservice.entity.MessageType;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.entity.ThreadInfo;
import com.bricklayers.chatservice.exception.ChatAccessDeniedException;
import com.bricklayers.chatservice.exception.InvalidChatOperationException;
import com.bricklayers.chatservice.exception.MessageNotFoundException;
import com.bricklayers.chatservice.mapper.MessageMapper;
import com.bricklayers.chatservice.repository.ChatBucketRepository;
import com.bricklayers.chatservice.repository.ChatRepository;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService.ChatAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceTest {

    private static final String CHAT_ID = "chat-1";
    private static final UserContext USER = new UserContext("u1", "User One", "c1", ParticipantRole.BUILDER);

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatBucketRepository bucketRepository;

    @Mock
    private ChatAccessService accessService;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatEventPublisher eventPublisher;

    @InjectMocks
    private MessageService messageService;

    @Test
    void sendMessageRejectsEmptyMessage() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(directChat(), ParticipantRole.BUILDER));

        assertThatThrownBy(() -> messageService.sendMessage(CHAT_ID, new SendMessageRequest("  ", null, null, List.of()), USER))
                .isInstanceOf(InvalidChatOperationException.class);
        verifyNoInteractions(bucketRepository, eventPublisher);
    }

    @Test
    void sendMessageToDirectChatSetsRecipientAndUpdatesPreview() {
        Chat chat = directChat();
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(chat, ParticipantRole.BUILDER));
        SendMessageRequest request = new SendMessageRequest("hello", " ", null, null);
        ChatMessage message = ChatMessage.builder().senderId("u1").content("hello").build();
        when(messageMapper.toNewMessage(eq(request), eq("u1"), eq("User One"), any(LocalDateTime.class))).thenReturn(message);
        MessageResponse own = response("m-own");
        MessageResponse broadcast = response("m-broadcast");
        when(messageMapper.toResponse(message, "u1")).thenReturn(own);
        when(messageMapper.toResponse(message, (String) null)).thenReturn(broadcast);

        MessageResponse result = messageService.sendMessage(CHAT_ID, request, USER);

        assertThat(result).isSameAs(own);
        assertThat(message.getRecipientId()).isEqualTo("u2");
        assertThat(message.getStatus()).isEqualTo(MessageStatus.RECEIVED);
        verify(bucketRepository).appendMessage(CHAT_ID, null, message);
        verify(chatRepository).updateLastMessage(eq(CHAT_ID), eq("hello"), any(LocalDateTime.class));
        verify(eventPublisher).publish(ChatEventType.NEW_MESSAGE, CHAT_ID, null, "u1", broadcast);
    }

    @Test
    void sendMessageWithAttachmentOnlyUsesAttachmentPreviewInGroupChat() {
        Chat chat = brigadeChat();
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(chat, ParticipantRole.BUILDER));
        SendMessageRequest request = new SendMessageRequest(null, null, null,
                List.of(new AttachmentDto("http://f", "f.png", "image/png", 10)));
        ChatMessage message = ChatMessage.builder().senderId("u1").recipientId("x").status(MessageStatus.READ).build();
        when(messageMapper.toNewMessage(eq(request), eq("u1"), eq("User One"), any(LocalDateTime.class))).thenReturn(message);

        messageService.sendMessage(CHAT_ID, request, USER);

        assertThat(message.getRecipientId()).isNull();
        assertThat(message.getStatus()).isNull();
        verify(chatRepository).updateLastMessage(eq(CHAT_ID), eq("Вкладення"), any(LocalDateTime.class));
    }

    @Test
    void sendMessageTruncatesLongPreview() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(brigadeChat(), ParticipantRole.BUILDER));
        String content = "a".repeat(150);
        SendMessageRequest request = new SendMessageRequest(content, null, null, null);
        ChatMessage message = ChatMessage.builder().senderId("u1").content(content).build();
        when(messageMapper.toNewMessage(eq(request), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(message);

        messageService.sendMessage(CHAT_ID, request, USER);

        verify(chatRepository).updateLastMessage(eq(CHAT_ID), eq("a".repeat(100) + "…"), any(LocalDateTime.class));
    }

    @Test
    void sendMessageIntoThreadRegistersReplyAndPublishesThreadUpdate() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(brigadeChat(), ParticipantRole.BUILDER));
        ThreadInfo info = ThreadInfo.builder().replyCount(1).participantIds(Set.of("u1")).build();
        ChatMessage parent = ChatMessage.builder().id("parent").thread(info).build();
        when(bucketRepository.findMessage(CHAT_ID, null, "parent")).thenReturn(Optional.of(parent));
        SendMessageRequest request = new SendMessageRequest("reply", "parent", null, null);
        ChatMessage message = ChatMessage.builder().senderId("u1").content("reply").build();
        when(messageMapper.toNewMessage(eq(request), anyString(), anyString(), any(LocalDateTime.class))).thenReturn(message);
        MessageResponse broadcast = response("b");
        when(messageMapper.toResponse(message, (String) null)).thenReturn(broadcast);
        MessageResponse own = response("own");
        when(messageMapper.toResponse(message, "u1")).thenReturn(own);
        ThreadInfoResponse threadResponse = new ThreadInfoResponse(1, null, Set.of("u1"));
        when(messageMapper.toResponse(info)).thenReturn(threadResponse);

        assertThat(messageService.sendMessage(CHAT_ID, request, USER)).isSameAs(own);

        verify(bucketRepository).appendMessage(CHAT_ID, "parent", message);
        verify(bucketRepository).registerThreadReply(eq(CHAT_ID), eq("parent"), eq("u1"), any(LocalDateTime.class));
        verify(chatRepository, never()).updateLastMessage(anyString(), anyString(), any());
        verify(eventPublisher).publish(ChatEventType.NEW_MESSAGE, CHAT_ID, "parent", "u1", broadcast);
        verify(eventPublisher).publish(ChatEventType.THREAD_REPLY, CHAT_ID, null, "u1",
                Map.of("parentMessageId", "parent", "thread", threadResponse));
    }

    @Test
    void sendMessageRejectsThreadUnderSystemMessage() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(brigadeChat(), ParticipantRole.BUILDER));
        ChatMessage parent = ChatMessage.builder().id("sys").type(MessageType.SYSTEM).build();
        when(bucketRepository.findMessage(CHAT_ID, null, "sys")).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> messageService.sendMessage(CHAT_ID, new SendMessageRequest("x", "sys", null, null), USER))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void sendMessageFailsWhenThreadParentIsMissing() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(brigadeChat(), ParticipantRole.BUILDER));
        when(bucketRepository.findMessage(CHAT_ID, null, "missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> messageService.sendMessage(CHAT_ID, new SendMessageRequest("x", "missing", null, null), USER))
                .isInstanceOf(MessageNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void sendMessageFailsWhenDirectChatHasNoRecipient() {
        Chat chat = directChat();
        chat.getParticipants().get(1).setLeftAt(LocalDateTime.now());
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(chat, ParticipantRole.BUILDER));
        SendMessageRequest request = new SendMessageRequest("hi", null, null, null);
        when(messageMapper.toNewMessage(eq(request), anyString(), anyString(), any(LocalDateTime.class)))
                .thenReturn(ChatMessage.builder().build());

        assertThatThrownBy(() -> messageService.sendMessage(CHAT_ID, request, USER))
                .isInstanceOf(InvalidChatOperationException.class)
                .hasMessage("У чаті немає отримувача");
        verify(bucketRepository, never()).appendMessage(any(), any(), any());
    }

    @Test
    void getMessagesForMainTimelineClampsNegativePage() {
        Slice<ChatBucket> slice = new SliceImpl<>(List.of());
        when(bucketRepository.findByChatIdAndThreadIdIsNullOrderByEndDateDesc(CHAT_ID, PageRequest.of(0, 2))).thenReturn(slice);
        MessagePageResponse page = new MessagePageResponse(List.of(), false, 0);
        when(messageMapper.toPage(slice, "u1")).thenReturn(page);

        assertThat(messageService.getMessages(CHAT_ID, "", -3, USER)).isSameAs(page);
        verify(accessService).requireReadAccess(CHAT_ID, USER);
    }

    @Test
    void getMessagesForThreadUsesThreadQuery() {
        Slice<ChatBucket> slice = new SliceImpl<>(List.of());
        when(bucketRepository.findByChatIdAndThreadIdOrderByEndDateDesc(CHAT_ID, "t1", PageRequest.of(2, 2))).thenReturn(slice);
        MessagePageResponse page = new MessagePageResponse(List.of(), true, 2);
        when(messageMapper.toPage(slice, "u1")).thenReturn(page);

        assertThat(messageService.getMessages(CHAT_ID, "t1", 2, USER)).isSameAs(page);
    }

    @Test
    void editMessageUpdatesOwnTextMessage() {
        ChatMessage message = ChatMessage.builder().id("m1").senderId("u1").content("old").build();
        when(bucketRepository.findMessage(CHAT_ID, "t1", "m1")).thenReturn(Optional.of(message));
        when(bucketRepository.editMessage(eq(CHAT_ID), eq("t1"), eq("m1"), eq("new"), any(LocalDateTime.class))).thenReturn(true);
        MessageResponse own = response("own");
        MessageResponse broadcast = response("broadcast");
        when(messageMapper.toResponse(message, "u1")).thenReturn(own);
        when(messageMapper.toResponse(message, (String) null)).thenReturn(broadcast);

        MessageResponse result = messageService.editMessage(CHAT_ID, "t1", "m1", new EditMessageRequest("new"), USER);

        assertThat(result).isSameAs(own);
        assertThat(message.getContent()).isEqualTo("new");
        assertThat(message.getEditedAt()).isNotNull();
        verify(accessService).requireWriteAccess(CHAT_ID, USER);
        verify(eventPublisher).publish(ChatEventType.MESSAGE_EDITED, CHAT_ID, "t1", "u1", broadcast);
    }

    @Test
    void editMessageRejectsForeignMessage() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("other").build()));

        assertThatThrownBy(() -> messageService.editMessage(CHAT_ID, null, "m1", new EditMessageRequest("x"), USER))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void editMessageRejectsDeletedMessage() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("u1").deleted(true).build()));

        assertThatThrownBy(() -> messageService.editMessage(CHAT_ID, null, "m1", new EditMessageRequest("x"), USER))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void editMessageRejectsNonTextMessage() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("u1").type(MessageType.ATTACHMENT).build()));

        assertThatThrownBy(() -> messageService.editMessage(CHAT_ID, null, "m1", new EditMessageRequest("x"), USER))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void editMessageFailsWhenRepositoryDidNotModify() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("u1").build()));
        when(bucketRepository.editMessage(eq(CHAT_ID), isNull(), eq("m1"), eq("x"), any(LocalDateTime.class))).thenReturn(false);

        assertThatThrownBy(() -> messageService.editMessage(CHAT_ID, null, "m1", new EditMessageRequest("x"), USER))
                .isInstanceOf(MessageNotFoundException.class);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteMessageByAuthorSoftDeletesAndPublishes() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(directChat(), ParticipantRole.BUILDER));
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("u1").build()));

        messageService.deleteMessage(CHAT_ID, null, "m1", USER);

        verify(bucketRepository).softDeleteMessage(CHAT_ID, null, "m1");
        verify(eventPublisher).publish(ChatEventType.MESSAGE_DELETED, CHAT_ID, null, "u1", "m1");
    }

    @Test
    void deleteMessageAlreadyDeletedIsNoOp() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(directChat(), ParticipantRole.BUILDER));
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("other").deleted(true).build()));

        messageService.deleteMessage(CHAT_ID, null, "m1", USER);

        verify(bucketRepository, never()).softDeleteMessage(any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteMessageByForemanInBrigadeChatIsAllowed() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(brigadeChat(), ParticipantRole.FOREMAN));
        when(bucketRepository.findMessage(CHAT_ID, "t1", "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("other").build()));

        messageService.deleteMessage(CHAT_ID, "t1", "m1", USER);

        verify(bucketRepository).softDeleteMessage(CHAT_ID, "t1", "m1");
    }

    @Test
    void deleteForeignMessageByBuilderIsDenied() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(brigadeChat(), ParticipantRole.BUILDER));
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("other").build()));

        assertThatThrownBy(() -> messageService.deleteMessage(CHAT_ID, null, "m1", USER))
                .isInstanceOf(ChatAccessDeniedException.class);
        verify(bucketRepository, never()).softDeleteMessage(any(), any(), any());
    }

    @Test
    void deleteForeignMessageByForemanInDirectChatIsDenied() {
        when(accessService.requireWriteAccess(CHAT_ID, USER)).thenReturn(access(directChat(), ParticipantRole.FOREMAN));
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").senderId("other").build()));

        assertThatThrownBy(() -> messageService.deleteMessage(CHAT_ID, null, "m1", USER))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void setLikeUpdatesAndPublishesLikesCount() {
        ChatMessage before = ChatMessage.builder().id("m1").build();
        ChatMessage after = ChatMessage.builder().id("m1").likedBy(Set.of("u1", "u3")).build();
        when(bucketRepository.findMessage(CHAT_ID, null, "m1")).thenReturn(Optional.of(before), Optional.of(after));
        MessageResponse response = response("m1");
        when(messageMapper.toResponse(after, "u1")).thenReturn(response);

        MessageResponse result = messageService.setLike(CHAT_ID, " ", "m1", true, USER);

        assertThat(result).isSameAs(response);
        verify(bucketRepository).setLike(CHAT_ID, null, "m1", "u1", true);
        verify(eventPublisher).publish(ChatEventType.LIKE_UPDATE, CHAT_ID, null, "u1",
                Map.of("messageId", "m1", "likesCount", 2));
    }

    @Test
    void setLikeOnDeletedMessageIsRejected() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1"))
                .thenReturn(Optional.of(ChatMessage.builder().id("m1").deleted(true).build()));

        assertThatThrownBy(() -> messageService.setLike(CHAT_ID, null, "m1", true, USER))
                .isInstanceOf(InvalidChatOperationException.class);
        verify(bucketRepository, never()).setLike(any(), any(), any(), any(), anyBoolean());
    }

    @Test
    void markDeliveredUpdatesReceivedMessage() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1")).thenReturn(Optional.of(
                ChatMessage.builder().id("m1").recipientId("u1").status(MessageStatus.RECEIVED).build()));

        messageService.markDelivered(CHAT_ID, null, "m1", USER);

        verify(accessService).requireReadAccess(CHAT_ID, USER);
        verify(bucketRepository).updateStatus(CHAT_ID, null, "m1", MessageStatus.DELIVERED);
        verify(eventPublisher).publish(ChatEventType.STATUS_UPDATE, CHAT_ID, null, "u1",
                Map.of("messageId", "m1", "status", MessageStatus.DELIVERED));
    }

    @Test
    void markDeliveredSkipsAlreadyDeliveredMessage() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1")).thenReturn(Optional.of(
                ChatMessage.builder().id("m1").recipientId("u1").status(MessageStatus.READ).build()));

        messageService.markDelivered(CHAT_ID, null, "m1", USER);

        verify(bucketRepository, never()).updateStatus(any(), any(), any(), any());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void markDeliveredByNonRecipientIsDenied() {
        when(bucketRepository.findMessage(CHAT_ID, null, "m1")).thenReturn(Optional.of(
                ChatMessage.builder().id("m1").recipientId("u2").status(MessageStatus.RECEIVED).build()));

        assertThatThrownBy(() -> messageService.markDelivered(CHAT_ID, null, "m1", USER))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    private static ChatAccess access(Chat chat, ParticipantRole role) {
        return new ChatAccess(chat, ChatParticipant.builder().userId("u1").role(role).build());
    }

    private static Chat directChat() {
        return Chat.builder()
                .id(CHAT_ID)
                .companyId("c1")
                .type(ChatType.FOREMAN_BUILDER)
                .participants(new ArrayList<>(List.of(
                        ChatParticipant.builder().userId("u1").role(ParticipantRole.BUILDER).build(),
                        ChatParticipant.builder().userId("u2").role(ParticipantRole.FOREMAN).build())))
                .build();
    }

    private static Chat brigadeChat() {
        return Chat.builder()
                .id(CHAT_ID)
                .companyId("c1")
                .type(ChatType.BRIGADE_ALL)
                .participants(new ArrayList<>(List.of(
                        ChatParticipant.builder().userId("u1").role(ParticipantRole.BUILDER).build())))
                .build();
    }

    private static MessageResponse response(String id) {
        return new MessageResponse(id, "u1", "User One", MessageType.TEXT, "c", null, null, false,
                null, null, 0, false, List.of(), null);
    }
}
