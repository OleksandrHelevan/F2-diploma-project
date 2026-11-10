package com.bricklayers.chatservice.service;

import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatEventType;
import com.bricklayers.chatservice.entity.ChatMessage;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.entity.ChatType;
import com.bricklayers.chatservice.entity.MessageType;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.exception.ChatNotFoundException;
import com.bricklayers.chatservice.mapper.MessageMapper;
import com.bricklayers.chatservice.repository.ChatBucketRepository;
import com.bricklayers.chatservice.repository.ChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatProvisioningServiceTest {

    @Mock
    private ChatRepository chatRepository;

    @Mock
    private ChatBucketRepository bucketRepository;

    @Mock
    private MessageMapper messageMapper;

    @Mock
    private ChatEventPublisher eventPublisher;

    @InjectMocks
    private ChatProvisioningService provisioningService;

    @Test
    void createBrigadeChatReturnsExistingChat() {
        Chat existing = Chat.builder().id("existing").build();
        when(chatRepository.findByBrigadeIdAndType("br1", ChatType.BRIGADE_ALL)).thenReturn(Optional.of(existing));

        assertThat(provisioningService.createBrigadeChat("c1", "br1", "Brigade", "f1", List.of("b1")))
                .isSameAs(existing);
        verify(chatRepository, never()).save(any());
    }

    @Test
    void createBrigadeChatSavesForemanAndBuilders() {
        when(chatRepository.findByBrigadeIdAndType("br1", ChatType.BRIGADE_ALL)).thenReturn(Optional.empty());
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Chat chat = provisioningService.createBrigadeChat("c1", "br1", "Brigade", "f1", List.of("b1", "b2"));

        assertThat(chat.getType()).isEqualTo(ChatType.BRIGADE_ALL);
        assertThat(chat.getCompanyId()).isEqualTo("c1");
        assertThat(chat.getName()).isEqualTo("Brigade");
        assertThat(chat.getBrigadeId()).isEqualTo("br1");
        assertThat(chat.getCreatedAt()).isNotNull();
        assertThat(chat.getParticipants()).extracting(ChatParticipant::getUserId, ChatParticipant::getRole)
                .containsExactly(
                        tuple("f1", ParticipantRole.FOREMAN),
                        tuple("b1", ParticipantRole.BUILDER),
                        tuple("b2", ParticipantRole.BUILDER));
    }

    @Test
    void createCustomerForemanChatReturnsExistingChat() {
        Chat existing = Chat.builder().id("existing").build();
        when(chatRepository.findByProjectIdAndType("p1", ChatType.CUSTOMER_FOREMAN)).thenReturn(Optional.of(existing));

        assertThat(provisioningService.createCustomerForemanChat("c1", "p1", "Project", "br1", "cu1", "f1"))
                .isSameAs(existing);
        verifyNoInteractions(bucketRepository, eventPublisher);
    }

    @Test
    void createCustomerForemanChatSavesChatAndPostsSystemMessage() {
        when(chatRepository.findByProjectIdAndType("p1", ChatType.CUSTOMER_FOREMAN)).thenReturn(Optional.empty());
        when(chatRepository.save(any(Chat.class))).thenAnswer(invocation -> {
            Chat chat = invocation.getArgument(0);
            chat.setId("new-chat");
            return chat;
        });
        ChatMessage system = ChatMessage.builder().type(MessageType.SYSTEM).build();
        when(messageMapper.toSystemMessage(eq("Бригаду призначено на об'єкт"), any(LocalDateTime.class))).thenReturn(system);
        MessageResponse systemResponse = new MessageResponse("s", null, null, MessageType.SYSTEM, "x",
                null, null, false, null, null, 0, false, List.of(), null);
        when(messageMapper.toResponse(system, (String) null)).thenReturn(systemResponse);

        Chat chat = provisioningService.createCustomerForemanChat("c1", "p1", "Project", "br1", "cu1", "f1");

        assertThat(chat.getId()).isEqualTo("new-chat");
        assertThat(chat.getProjectId()).isEqualTo("p1");
        assertThat(chat.getParticipants()).extracting(ChatParticipant::getUserId, ChatParticipant::getRole)
                .containsExactly(tuple("cu1", ParticipantRole.CUSTOMER), tuple("f1", ParticipantRole.FOREMAN));
        verify(bucketRepository).appendMessage("new-chat", null, system);
        verify(chatRepository).updateLastMessage(eq("new-chat"), eq("Бригаду призначено на об'єкт"), any(LocalDateTime.class));
        verify(eventPublisher).publish(ChatEventType.NEW_MESSAGE, "new-chat", null, null, systemResponse);
    }

    @Test
    void reassignBrigadeReplacesForemanAndPostsSystemMessage() {
        Chat chat = Chat.builder().id("chat-1").participants(new ArrayList<>(List.of(
                participant("cu1", ParticipantRole.CUSTOMER),
                participant("f-old", ParticipantRole.FOREMAN)))).build();
        when(chatRepository.findByProjectIdAndType("p1", ChatType.CUSTOMER_FOREMAN)).thenReturn(Optional.of(chat));
        when(messageMapper.toSystemMessage(anyString(), any(LocalDateTime.class))).thenReturn(ChatMessage.builder().build());

        provisioningService.reassignBrigade("p1", "br2", "f-new");

        verify(chatRepository).setBrigade("chat-1", "br2");
        verify(chatRepository).markParticipantLeft(eq("chat-1"), eq("f-old"), any(LocalDateTime.class));
        verify(chatRepository).addParticipant(eq("chat-1"),
                argThat(p -> p.getUserId().equals("f-new") && p.getRole() == ParticipantRole.FOREMAN));
        verify(eventPublisher).publish(ChatEventType.PARTICIPANT_REMOVED, "chat-1", null, "f-old", "f-old");
        verify(eventPublisher).publish(ChatEventType.PARTICIPANT_ADDED, "chat-1", null, "f-new", "f-new");
        verify(chatRepository).updateLastMessage(eq("chat-1"), eq("Бригаду змінено"), any(LocalDateTime.class));
    }

    @Test
    void reassignBrigadeFailsWhenProjectChatMissing() {
        when(chatRepository.findByProjectIdAndType("p1", ChatType.CUSTOMER_FOREMAN)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisioningService.reassignBrigade("p1", "br2", "f-new"))
                .isInstanceOf(ChatNotFoundException.class)
                .hasMessageContaining("project:p1");
    }

    @Test
    void syncBrigadeMembersAddsAndRemovesBuildersAndUpdatesProjectChats() {
        ChatParticipant left = participant("b-left", ParticipantRole.BUILDER);
        left.setLeftAt(LocalDateTime.now());
        Chat brigade = Chat.builder().id("brigade-chat").type(ChatType.BRIGADE_ALL).participants(new ArrayList<>(List.of(
                participant("f1", ParticipantRole.FOREMAN),
                participant("b-keep", ParticipantRole.BUILDER),
                participant("b-remove", ParticipantRole.BUILDER),
                left))).build();
        when(chatRepository.findByBrigadeIdAndType("br1", ChatType.BRIGADE_ALL)).thenReturn(Optional.of(brigade));
        when(messageMapper.toSystemMessage(anyString(), any(LocalDateTime.class))).thenReturn(ChatMessage.builder().build());
        Chat activeProject = Chat.builder().id("project-chat").type(ChatType.CUSTOMER_FOREMAN)
                .participants(new ArrayList<>(List.of(participant("f-old", ParticipantRole.FOREMAN)))).build();
        Chat archivedProject = Chat.builder().id("archived-chat").type(ChatType.CUSTOMER_FOREMAN).archived(true)
                .participants(new ArrayList<>(List.of(participant("f-old", ParticipantRole.FOREMAN)))).build();
        when(chatRepository.findByBrigadeId("br1")).thenReturn(List.of(brigade, activeProject, archivedProject));

        provisioningService.syncBrigadeMembers("br1", "f1", Set.of("b-keep", "b-new"));

        verify(chatRepository).addParticipant(eq("brigade-chat"),
                argThat(p -> p.getUserId().equals("b-new") && p.getRole() == ParticipantRole.BUILDER));
        verify(eventPublisher).publish(ChatEventType.PARTICIPANT_ADDED, "brigade-chat", null, "b-new", "b-new");
        verify(chatRepository).markParticipantLeft(eq("brigade-chat"), eq("b-remove"), any(LocalDateTime.class));
        verify(eventPublisher).publish(ChatEventType.PARTICIPANT_REMOVED, "brigade-chat", null, "b-remove", "b-remove");
        verify(chatRepository, never()).markParticipantLeft(eq("brigade-chat"), eq("f1"), any());
        verify(chatRepository).updateLastMessage(eq("brigade-chat"), eq("Склад бригади оновлено"), any(LocalDateTime.class));
        verify(chatRepository).markParticipantLeft(eq("project-chat"), eq("f-old"), any(LocalDateTime.class));
        verify(chatRepository, never()).markParticipantLeft(eq("archived-chat"), anyString(), any());
    }

    @Test
    void syncBrigadeMembersWithoutChangesPostsNoSystemMessage() {
        Chat brigade = Chat.builder().id("brigade-chat").type(ChatType.BRIGADE_ALL).participants(new ArrayList<>(List.of(
                participant("f1", ParticipantRole.FOREMAN),
                participant("b1", ParticipantRole.BUILDER)))).build();
        when(chatRepository.findByBrigadeIdAndType("br1", ChatType.BRIGADE_ALL)).thenReturn(Optional.of(brigade));
        when(chatRepository.findByBrigadeId("br1")).thenReturn(List.of());

        provisioningService.syncBrigadeMembers("br1", "f1", Set.of("b1"));

        verify(chatRepository, never()).addParticipant(anyString(), any());
        verify(chatRepository, never()).markParticipantLeft(anyString(), anyString(), any());
        verifyNoInteractions(bucketRepository, eventPublisher, messageMapper);
    }

    @Test
    void syncBrigadeMembersFailsWhenBrigadeChatMissing() {
        when(chatRepository.findByBrigadeIdAndType("br1", ChatType.BRIGADE_ALL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisioningService.syncBrigadeMembers("br1", "f1", Set.of()))
                .isInstanceOf(ChatNotFoundException.class)
                .hasMessageContaining("brigade:br1");
    }

    @Test
    void syncBrigadeMembersAddsForemanWhenNoneIsActive() {
        Chat brigade = Chat.builder().id("brigade-chat").type(ChatType.BRIGADE_ALL)
                .participants(new ArrayList<>()).build();
        when(chatRepository.findByBrigadeIdAndType("br1", ChatType.BRIGADE_ALL)).thenReturn(Optional.of(brigade));
        when(chatRepository.findByBrigadeId("br1")).thenReturn(List.of());
        when(messageMapper.toSystemMessage(anyString(), any(LocalDateTime.class))).thenReturn(ChatMessage.builder().build());

        provisioningService.syncBrigadeMembers("br1", "f1", Set.of());

        verify(chatRepository).addParticipant(eq("brigade-chat"),
                argThat(p -> p.getUserId().equals("f1") && p.getRole() == ParticipantRole.FOREMAN));
        verify(chatRepository, never()).markParticipantLeft(anyString(), anyString(), any());
        verify(bucketRepository).appendMessage(eq("brigade-chat"), isNull(), any(ChatMessage.class));
    }

    @Test
    void archiveProjectChatPostsMessageAndArchives() {
        when(chatRepository.findByProjectIdAndType("p1", ChatType.CUSTOMER_FOREMAN))
                .thenReturn(Optional.of(Chat.builder().id("chat-1").build()));
        when(messageMapper.toSystemMessage(anyString(), any(LocalDateTime.class))).thenReturn(ChatMessage.builder().build());

        provisioningService.archiveProjectChat("p1");

        verify(chatRepository).updateLastMessage(eq("chat-1"), eq("Об'єкт завершено, чат архівовано"), any(LocalDateTime.class));
        verify(chatRepository).setArchived("chat-1", true);
    }

    @Test
    void archiveProjectChatIgnoresMissingChat() {
        when(chatRepository.findByProjectIdAndType("p1", ChatType.CUSTOMER_FOREMAN)).thenReturn(Optional.empty());

        provisioningService.archiveProjectChat("p1");

        verify(chatRepository, never()).setArchived(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void addCompanyAdminAddsOnlyWhenNotParticipant() {
        Chat chat = Chat.builder().id("chat-1").participants(new ArrayList<>(List.of(
                participant("admin-1", ParticipantRole.COMPANY_ADMIN)))).build();
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat));

        provisioningService.addCompanyAdmin("chat-1", "admin-1");
        provisioningService.addCompanyAdmin("chat-1", "admin-2");

        verify(chatRepository).addParticipant(eq("chat-1"),
                argThat(p -> p.getUserId().equals("admin-2") && p.getRole() == ParticipantRole.COMPANY_ADMIN));
        verify(chatRepository, never()).addParticipant(eq("chat-1"), argThat(p -> p.getUserId().equals("admin-1")));
    }

    @Test
    void addCompanyAdminFailsForMissingChat() {
        when(chatRepository.findById("chat-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> provisioningService.addCompanyAdmin("chat-1", "admin"))
                .isInstanceOf(ChatNotFoundException.class);
    }

    private static ChatParticipant participant(String userId, ParticipantRole role) {
        return ChatParticipant.builder().userId(userId).role(role).joinedAt(LocalDateTime.now()).build();
    }
}
