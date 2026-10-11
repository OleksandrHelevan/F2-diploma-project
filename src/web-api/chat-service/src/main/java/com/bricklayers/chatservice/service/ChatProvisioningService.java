package com.bricklayers.chatservice.service;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatProvisioningService
  @version  1.0.0 
  @since 06.10.2026 - 22.13
*/

import com.bricklayers.chatservice.entity.*;
import com.bricklayers.chatservice.exception.ChatNotFoundException;
import com.bricklayers.chatservice.mapper.MessageMapper;
import com.bricklayers.chatservice.repository.ChatBucketRepository;
import com.bricklayers.chatservice.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatProvisioningService {

    private final ChatRepository chatRepository;
    private final ChatBucketRepository bucketRepository;
    private final MessageMapper messageMapper;
    private final ChatEventPublisher eventPublisher;

    public Chat createBrigadeChat(String companyId, String brigadeId, String brigadeName,
                                  String foremanId, Collection<String> builderIds) {
        return chatRepository.findByBrigadeIdAndType(brigadeId, ChatType.BRIGADE_ALL).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            List<ChatParticipant> participants = new ArrayList<>();
            participants.add(participant(foremanId, ParticipantRole.FOREMAN, now));
            builderIds.forEach(id -> participants.add(participant(id, ParticipantRole.BUILDER, now)));

            return chatRepository.save(Chat.builder()
                    .companyId(companyId)
                    .type(ChatType.BRIGADE_ALL)
                    .name(brigadeName)
                    .brigadeId(brigadeId)
                    .participants(participants)
                    .createdAt(now)
                    .lastMessageAt(now)
                    .build());
        });
    }

    public Chat createCustomerForemanChat(String companyId, String projectId, String projectName,
                                          String brigadeId, String customerId, String foremanId) {
        return chatRepository.findByProjectIdAndType(projectId, ChatType.CUSTOMER_FOREMAN).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            Chat chat = chatRepository.save(Chat.builder()
                    .companyId(companyId)
                    .type(ChatType.CUSTOMER_FOREMAN)
                    .name(projectName)
                    .projectId(projectId)
                    .brigadeId(brigadeId)
                    .participants(new ArrayList<>(List.of(
                            participant(customerId, ParticipantRole.CUSTOMER, now),
                            participant(foremanId, ParticipantRole.FOREMAN, now))))
                    .createdAt(now)
                    .lastMessageAt(now)
                    .build());

            postSystemMessage(chat.getId(), "Бригаду призначено на об'єкт", now);
            return chat;
        });
    }

    public void reassignBrigade(String projectId, String newBrigadeId, String newForemanId) {
        Chat chat = chatRepository.findByProjectIdAndType(projectId, ChatType.CUSTOMER_FOREMAN)
                .orElseThrow(() -> new ChatNotFoundException("project:" + projectId));

        LocalDateTime now = LocalDateTime.now();
        chatRepository.setBrigade(chat.getId(), newBrigadeId);
        replaceParticipant(chat, ParticipantRole.FOREMAN, newForemanId, now);
        postSystemMessage(chat.getId(), "Бригаду змінено", now);
    }

    public void syncBrigadeMembers(String brigadeId, String foremanId, Set<String> builderIds) {
        Chat brigadeChat = chatRepository.findByBrigadeIdAndType(brigadeId, ChatType.BRIGADE_ALL)
                .orElseThrow(() -> new ChatNotFoundException("brigade:" + brigadeId));

        LocalDateTime now = LocalDateTime.now();
        boolean changed = replaceParticipant(brigadeChat, ParticipantRole.FOREMAN, foremanId, now);

        Set<String> activeBuilders = brigadeChat.getParticipants().stream()
                .filter(p -> p.getLeftAt() == null && p.getRole() == ParticipantRole.BUILDER)
                .map(ChatParticipant::getUserId)
                .collect(Collectors.toSet());

        for (String id : builderIds) {
            if (!activeBuilders.contains(id)) {
                chatRepository.addParticipant(brigadeChat.getId(), participant(id, ParticipantRole.BUILDER, now));
                eventPublisher.publish(ChatEventType.PARTICIPANT_ADDED, brigadeChat.getId(), null, id, id);
                changed = true;
            }
        }
        for (String id : activeBuilders) {
            if (!builderIds.contains(id)) {
                chatRepository.markParticipantLeft(brigadeChat.getId(), id, now);
                eventPublisher.publish(ChatEventType.PARTICIPANT_REMOVED, brigadeChat.getId(), null, id, id);
                changed = true;
            }
        }
        if (changed) {
            postSystemMessage(brigadeChat.getId(), "Склад бригади оновлено", now);
        }

        for (Chat projectChat : chatRepository.findByBrigadeId(brigadeId)) {
            if (projectChat.getType() == ChatType.CUSTOMER_FOREMAN && !projectChat.isArchived()) {
                replaceParticipant(projectChat, ParticipantRole.FOREMAN, foremanId, now);
            }
        }
    }

    public void archiveProjectChat(String projectId) {
        chatRepository.findByProjectIdAndType(projectId, ChatType.CUSTOMER_FOREMAN).ifPresent(chat -> {
            postSystemMessage(chat.getId(), "Об'єкт завершено, чат архівовано", LocalDateTime.now());
            chatRepository.setArchived(chat.getId(), true);
        });
    }

    public void addCompanyAdmin(String chatId, String adminId) {
        Chat chat = chatRepository.findById(chatId).orElseThrow(() -> new ChatNotFoundException(chatId));
        if (ChatAccessService.activeParticipant(chat, adminId).isEmpty()) {
            chatRepository.addParticipant(chatId, participant(adminId, ParticipantRole.COMPANY_ADMIN, LocalDateTime.now()));
        }
    }


    private boolean replaceParticipant(Chat chat, ParticipantRole role, String newUserId, LocalDateTime now) {
        Optional<ChatParticipant> current = chat.getParticipants().stream()
                .filter(p -> p.getLeftAt() == null && p.getRole() == role)
                .findFirst();

        if (current.isPresent() && current.get().getUserId().equals(newUserId)) {
            return false;
        }

        current.ifPresent(p -> {
            chatRepository.markParticipantLeft(chat.getId(), p.getUserId(), now);
            eventPublisher.publish(ChatEventType.PARTICIPANT_REMOVED, chat.getId(), null, p.getUserId(), p.getUserId());
        });

        chatRepository.addParticipant(chat.getId(), participant(newUserId, role, now));
        eventPublisher.publish(ChatEventType.PARTICIPANT_ADDED, chat.getId(), null, newUserId, newUserId);
        return true;
    }

    private void postSystemMessage(String chatId, String text, LocalDateTime now) {
        ChatMessage message = messageMapper.toSystemMessage(text, now);
        bucketRepository.appendMessage(chatId, null, message);
        chatRepository.updateLastMessage(chatId, text, now);
        eventPublisher.publish(ChatEventType.NEW_MESSAGE, chatId, null, null,
                messageMapper.toResponse(message, null));
    }

    private static ChatParticipant participant(String userId, ParticipantRole role, LocalDateTime now) {
        return ChatParticipant.builder().userId(userId).role(role).joinedAt(now).build();
    }
}
