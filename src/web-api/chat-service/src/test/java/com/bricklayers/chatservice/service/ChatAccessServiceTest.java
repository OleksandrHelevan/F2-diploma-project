package com.bricklayers.chatservice.service;

import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatParticipant;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.exception.ChatAccessDeniedException;
import com.bricklayers.chatservice.exception.ChatArchivedException;
import com.bricklayers.chatservice.exception.ChatNotFoundException;
import com.bricklayers.chatservice.repository.ChatRepository;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService.ChatAccess;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatAccessServiceTest {

    private static final UserContext USER = new UserContext("u1", "User", "c1", ParticipantRole.BUILDER);

    @Mock
    private ChatRepository chatRepository;

    @InjectMocks
    private ChatAccessService accessService;

    @Test
    void readAccessReturnsChatAndActiveParticipant() {
        ChatParticipant participant = ChatParticipant.builder().userId("u1").role(ParticipantRole.BUILDER).build();
        Chat chat = chat("c1", false, participant);
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat));

        ChatAccess access = accessService.requireReadAccess("chat-1", USER);

        assertThat(access.chat()).isSameAs(chat);
        assertThat(access.participant()).isSameAs(participant);
    }

    @Test
    void readAccessHidesChatOfOtherCompany() {
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat("c2", false,
                ChatParticipant.builder().userId("u1").build())));

        assertThatThrownBy(() -> accessService.requireReadAccess("chat-1", USER))
                .isInstanceOf(ChatNotFoundException.class)
                .satisfies(e -> assertThat(((ChatNotFoundException) e).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void readAccessHidesChatFromFormerParticipant() {
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat("c1", false,
                ChatParticipant.builder().userId("u1").leftAt(LocalDateTime.now()).build())));

        assertThatThrownBy(() -> accessService.requireReadAccess("chat-1", USER))
                .isInstanceOf(ChatNotFoundException.class);
    }

    @Test
    void readAccessFailsForMissingChat() {
        when(chatRepository.findById("chat-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accessService.requireReadAccess("chat-1", USER))
                .isInstanceOf(ChatNotFoundException.class)
                .hasMessageContaining("chat-1");
    }

    @Test
    void writeAccessRejectsArchivedChat() {
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat("c1", true,
                ChatParticipant.builder().userId("u1").role(ParticipantRole.BUILDER).build())));

        assertThatThrownBy(() -> accessService.requireWriteAccess("chat-1", USER))
                .isInstanceOf(ChatArchivedException.class)
                .satisfies(e -> assertThat(((ChatArchivedException) e).getCode()).isEqualTo("CHAT_ARCHIVED"));
    }

    @Test
    void writeAccessRejectsCompanyAdmin() {
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat("c1", false,
                ChatParticipant.builder().userId("u1").role(ParticipantRole.COMPANY_ADMIN).build())));

        assertThatThrownBy(() -> accessService.requireWriteAccess("chat-1", USER))
                .isInstanceOf(ChatAccessDeniedException.class);
    }

    @Test
    void writeAccessAllowsActiveMember() {
        Chat chat = chat("c1", false, ChatParticipant.builder().userId("u1").role(ParticipantRole.BUILDER).build());
        when(chatRepository.findById("chat-1")).thenReturn(Optional.of(chat));

        assertThat(accessService.requireWriteAccess("chat-1", USER).chat()).isSameAs(chat);
    }

    private static Chat chat(String companyId, boolean archived, ChatParticipant... participants) {
        return Chat.builder().id("chat-1").companyId(companyId).archived(archived)
                .participants(new ArrayList<>(List.of(participants))).build();
    }
}
