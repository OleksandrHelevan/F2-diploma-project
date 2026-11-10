package com.bricklayers.chatservice.service;

import com.bricklayers.chatservice.client.UserDirectory;
import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.dto.CreateDirectChatRequest;
import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatEventType;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.entity.ChatType;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.exception.ChatAccessDeniedException;
import com.bricklayers.chatservice.exception.InvalidChatOperationException;
import com.bricklayers.chatservice.mapper.ChatMapper;
import com.bricklayers.chatservice.repository.ChatBucketRepository;
import com.bricklayers.chatservice.repository.ChatRepository;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService.ChatAccess;
import com.bricklayers.common.dto.UserInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    private static final UserContext FOREMAN = new UserContext("f1", "Foreman", "c1", ParticipantRole.FOREMAN);

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatBucketRepository bucketRepository;

    @Mock
    private ChatAccessService accessService;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private ChatMapper chatMapper;

    @Mock
    private ChatEventPublisher eventPublisher;

    @InjectMocks
    private ChatService chatService;

    @Test
    void getMyChatsMapsEachChatWithUnreadCountSinceLastRead() {
        LocalDateTime lastRead = LocalDateTime.of(2026, 1, 1, 10, 0);
        Chat chat = chat("chat-1", ChatType.BRIGADE_ALL,
                ChatParticipant.builder().userId("f1").role(ParticipantRole.FOREMAN)
                        .joinedAt(lastRead.minusDays(1)).lastReadAt(lastRead).build());
        Pageable pageable = PageRequest.of(0, 10);
        when(chatRepository.findActiveChatsForUser("c1", "f1", pageable)).thenReturn(new PageImpl<>(List.of(chat)));
        when(bucketRepository.countUnread("chat-1", "f1", lastRead)).thenReturn(4L);
        ChatResponse response = response("chat-1", 4);
        when(chatMapper.toResponse(chat, 4)).thenReturn(response);

        Page<ChatResponse> result = chatService.getMyChats(FOREMAN, pageable);

        assertThat(result.getContent()).containsExactly(response);
    }

    @Test
    void getChatUsesJoinDateWhenNeverReadAndZeroForNonParticipant() {
        LocalDateTime joined = LocalDateTime.of(2026, 2, 2, 8, 0);
        Chat chat = chat("chat-1", ChatType.BRIGADE_ALL,
                ChatParticipant.builder().userId("f1").role(ParticipantRole.FOREMAN).joinedAt(joined).build());
        when(accessService.requireReadAccess("chat-1", FOREMAN)).thenReturn(new ChatAccess(chat, chat.getParticipants().get(0)));
        when(bucketRepository.countUnread("chat-1", "f1", joined)).thenReturn(2L);
        ChatResponse response = response("chat-1", 2);
        when(chatMapper.toResponse(chat, 2)).thenReturn(response);

        assertThat(chatService.getChat("chat-1", FOREMAN)).isSameAs(response);

        Chat other = chat("chat-2", ChatType.BRIGADE_ALL,
                ChatParticipant.builder().userId("someone").role(ParticipantRole.BUILDER).build());
        Page<Chat> page = new PageImpl<>(List.of(other));
        when(chatRepository.findActiveChatsForUser("c1", "f1", Pageable.unpaged())).thenReturn(page);
        chatService.getMyChats(FOREMAN, Pageable.unpaged());
        verify(chatMapper).toResponse(other, 0);
    }

    @Test
    void createDirectChatRejectsNonDirectType() {
        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.BRIGADE_ALL, "b1"), FOREMAN))
                .isInstanceOf(InvalidChatOperationException.class);
        verifyNoInteractions(userDirectory);
    }

    @Test
    void createDirectChatRequiresForeman() {
        UserContext builder = new UserContext("b1", "B", "c1", ParticipantRole.BUILDER);

        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "f1"), builder))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void createDirectChatRejectsChatWithSelf() {
        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "f1"), FOREMAN))
                .isInstanceOf(InvalidChatOperationException.class)
                .hasMessage("Не можна створити чат із самим собою");
    }

    @Test
    void createDirectChatRejectsInactiveTarget() {
        when(userDirectory.getUser("b1")).thenReturn(new UserInfo("b1", "b", "B", "One", "c1", "BUILDER", false));

        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "b1"), FOREMAN))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void createDirectChatRejectsUnknownTargetRole() {
        when(userDirectory.getUser("b1")).thenReturn(new UserInfo("b1", "b", "B", "One", "c1", "SUPERADMIN", true));

        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "b1"), FOREMAN))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void createDirectChatRejectsRoleMismatch() {
        when(userDirectory.getUser("s1")).thenReturn(new UserInfo("s1", "s", "S", "One", "c1", "BUILDER", true));

        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_SUPPLIER, "s1"), FOREMAN))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void createDirectChatRejectsOtherCompany() {
        when(userDirectory.getUser("b1")).thenReturn(new UserInfo("b1", "b", "B", "One", "c2", "BUILDER", true));

        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "b1"), FOREMAN))
                .isInstanceOf(InvalidChatOperationException.class);
    }

    @Test
    void createDirectChatReturnsExistingChat() {
        when(userDirectory.getUser("a1")).thenReturn(new UserInfo("a1", "a", "A", "One", "c1", "BUILDER", true));
        Chat existing = chat("chat-x", ChatType.FOREMAN_BUILDER);
        when(chatRepository.findByDirectKey("FOREMAN_BUILDER:a1_f1")).thenReturn(Optional.of(existing));
        ChatResponse response = response("chat-x", 0);
        when(chatMapper.toResponse(existing, 0)).thenReturn(response);

        assertThat(chatService.createDirectChat(new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "a1"), FOREMAN))
                .isSameAs(response);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void createDirectChatCreatesNewChatWithBothParticipants() {
        when(userDirectory.getUser("s1")).thenReturn(new UserInfo("s1", "s", "S", "One", "c1", "SUPPLIER", true));
        when(chatRepository.findByDirectKey("FOREMAN_SUPPLIER:f1_s1")).thenReturn(Optional.empty());
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(chatMapper.toResponse(any(Chat.class), eq(0))).thenReturn(response("new", 0));

        chatService.createDirectChat(new CreateDirectChatRequest(ChatType.FOREMAN_SUPPLIER, "s1"), FOREMAN);

        ArgumentCaptor<Chat> captor = ArgumentCaptor.forClass(Chat.class);
        verify(chatRepository).save(captor.capture());
        Chat saved = captor.getValue();
        assertThat(saved.getCompanyId()).isEqualTo("c1");
        assertThat(saved.getType()).isEqualTo(ChatType.FOREMAN_SUPPLIER);
        assertThat(saved.getDirectKey()).isEqualTo("FOREMAN_SUPPLIER:f1_s1");
        assertThat(saved.getParticipants()).extracting(ChatParticipant::getUserId, ChatParticipant::getRole)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("f1", ParticipantRole.FOREMAN),
                        org.assertj.core.groups.Tuple.tuple("s1", ParticipantRole.SUPPLIER));
    }

    @Test
    void createDirectChatFallsBackToExistingOnDuplicateKey() {
        when(userDirectory.getUser("s1")).thenReturn(new UserInfo("s1", "s", "S", "One", "c1", "SUPPLIER", true));
        Chat concurrent = chat("concurrent", ChatType.FOREMAN_SUPPLIER);
        when(chatRepository.findByDirectKey("FOREMAN_SUPPLIER:f1_s1"))
                .thenReturn(Optional.empty(), Optional.of(concurrent));
        when(chatRepository.save(any(Chat.class))).thenThrow(new DuplicateKeyException("dup"));
        ChatResponse response = response("concurrent", 0);
        when(chatMapper.toResponse(concurrent, 0)).thenReturn(response);

        assertThat(chatService.createDirectChat(new CreateDirectChatRequest(ChatType.FOREMAN_SUPPLIER, "s1"), FOREMAN))
                .isSameAs(response);
    }

    @Test
    void createDirectChatRethrowsDuplicateKeyWhenChatStillMissing() {
        when(userDirectory.getUser("s1")).thenReturn(new UserInfo("s1", "s", "S", "One", "c1", "SUPPLIER", true));
        when(chatRepository.findByDirectKey("FOREMAN_SUPPLIER:f1_s1")).thenReturn(Optional.empty());
        DuplicateKeyException duplicate = new DuplicateKeyException("dup");
        when(chatRepository.save(any(Chat.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> chatService.createDirectChat(
                new CreateDirectChatRequest(ChatType.FOREMAN_SUPPLIER, "s1"), FOREMAN))
                .isSameAs(duplicate);
    }

    @Test
    void markAsReadInDirectChatMarksMessagesAndPublishesStatus() {
        Chat chat = chat("chat-1", ChatType.FOREMAN_BUILDER);
        when(accessService.requireReadAccess("chat-1", FOREMAN)).thenReturn(new ChatAccess(chat, null));

        chatService.markAsRead("chat-1", FOREMAN);

        verify(chatRepository).markAsRead(eq("chat-1"), eq("f1"), any(LocalDateTime.class));
        verify(bucketRepository).markDirectMessagesRead("chat-1", "f1");
        verify(eventPublisher).publish(eq(ChatEventType.STATUS_UPDATE), eq("chat-1"), eq(null), eq("f1"), anyMap());
    }

    @Test
    void markAsReadInGroupChatOnlyUpdatesReadMarker() {
        Chat chat = chat("chat-1", ChatType.BRIGADE_ALL);
        when(accessService.requireReadAccess("chat-1", FOREMAN)).thenReturn(new ChatAccess(chat, null));

        chatService.markAsRead("chat-1", FOREMAN);

        verify(chatRepository).markAsRead(eq("chat-1"), eq("f1"), any(LocalDateTime.class));
        verifyNoInteractions(bucketRepository, eventPublisher);
    }

    @Test
    void directKeyIsOrderIndependent() {
        assertThat(ChatService.directKey(ChatType.FOREMAN_BUILDER, "b", "a"))
                .isEqualTo(ChatService.directKey(ChatType.FOREMAN_BUILDER, "a", "b"))
                .isEqualTo("FOREMAN_BUILDER:a_b");
    }

    private static Chat chat(String id, ChatType type, ChatParticipant... participants) {
        return Chat.builder().id(id).companyId("c1").type(type)
                .participants(new ArrayList<>(List.of(participants))).build();
    }

    private static ChatResponse response(String id, int unread) {
        return new ChatResponse(id, ChatType.BRIGADE_ALL, null, null, null, List.of(), null, null, unread, false);
    }
}
