package com.bricklayers.chatservice.service;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatAccessService
  @version  1.0.0 
  @since 06.10.2026 - 22.11
*/

import com.bricklayers.chatservice.entity.*;
import com.bricklayers.chatservice.exception.*;
import com.bricklayers.chatservice.repository.ChatRepository;
import com.bricklayers.chatservice.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatAccessService {

    public record ChatAccess(Chat chat, ChatParticipant participant) {}

    private final ChatRepository chatRepository;

    public ChatAccess requireReadAccess(String chatId, UserContext user) {
        Chat chat = chatRepository.findById(chatId)
                .filter(c -> c.getCompanyId().equals(user.companyId()))
                .orElseThrow(() -> new ChatNotFoundException(chatId));

        ChatParticipant participant = activeParticipant(chat, user.userId())
                .orElseThrow(() -> new ChatNotFoundException(chatId));

        return new ChatAccess(chat, participant);
    }

    public ChatAccess requireWriteAccess(String chatId, UserContext user) {
        ChatAccess access = requireReadAccess(chatId, user);

        if (access.chat().isArchived()) {
            throw new ChatArchivedException(chatId);
        }
        if (access.participant().getRole() == ParticipantRole.COMPANY_ADMIN) {
            throw new ChatAccessDeniedException("Адміністратор компанії має доступ лише для читання");
        }
        return access;
    }

    public static Optional<ChatParticipant> activeParticipant(Chat chat, String userId) {
        return chat.getParticipants().stream()
                .filter(p -> p.getLeftAt() == null && p.getUserId().equals(userId))
                .findFirst();
    }
}
