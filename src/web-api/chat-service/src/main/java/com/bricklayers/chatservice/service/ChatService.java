package com.bricklayers.chatservice.service;

/*
  @author   user
  @project   F2-diploma-project
  @class  ChatService
  @version  1.0.0 
  @since 06.10.2026 - 22.11
*/

import com.bricklayers.chatservice.client.UserDirectory;
import com.bricklayers.chatservice.client.UserInfo;
import com.bricklayers.chatservice.dto.CreateDirectChatRequest;
import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.entity.*;
import com.bricklayers.chatservice.exception.*;
import com.bricklayers.chatservice.mapper.ChatMapper;
import com.bricklayers.chatservice.repository.ChatBucketRepository;
import com.bricklayers.chatservice.repository.ChatRepository;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService.ChatAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ChatService {

    private static final Set<ChatType> DIRECT_TYPES =
            EnumSet.of(ChatType.FOREMAN_BUILDER, ChatType.FOREMAN_SUPPLIER);

    private final ChatRepository chatRepository;
    private final ChatBucketRepository bucketRepository;
    private final ChatAccessService accessService;
    private final UserDirectory userDirectory;
    private final ChatMapper chatMapper;
    private final ChatEventPublisher eventPublisher;

    public Page<ChatResponse> getMyChats(UserContext user, Pageable pageable) {
        return chatRepository.findActiveChatsForUser(user.companyId(), user.userId(), pageable)
                .map(chat -> toResponse(chat, user.userId()));
    }

    public ChatResponse getChat(String chatId, UserContext user) {
        ChatAccess access = accessService.requireReadAccess(chatId, user);
        return toResponse(access.chat(), user.userId());
    }

    public ChatResponse createDirectChat(CreateDirectChatRequest request, UserContext user) {
        if (!DIRECT_TYPES.contains(request.type())) {
            throw new InvalidChatOperationException(
                    "Вручну можна створити лише FOREMAN_BUILDER або FOREMAN_SUPPLIER");
        }
        if (user.role() != ParticipantRole.FOREMAN) {
            throw new ChatAccessDeniedException("Direct-чати створює лише прораб");
        }
        if (user.userId().equals(request.targetUserId())) {
            throw new InvalidChatOperationException("Не можна створити чат із самим собою");
        }

        UserInfo target = userDirectory.getUser(request.targetUserId());
        ParticipantRole expectedRole = request.type() == ChatType.FOREMAN_BUILDER
                ? ParticipantRole.BUILDER
                : ParticipantRole.SUPPLIER;

        if (target.role() != expectedRole || !user.companyId().equals(target.companyId())) {
            throw new InvalidChatOperationException("Цей користувач не підходить для чату такого типу");
        }

        String directKey = directKey(request.type(), user.userId(), target.id());
        Chat chat = chatRepository.findByDirectKey(directKey)
                .orElseGet(() -> createDirect(request.type(), directKey, user, target));

        return toResponse(chat, user.userId());
    }

    public void markAsRead(String chatId, UserContext user) {
        ChatAccess access = accessService.requireReadAccess(chatId, user);
        LocalDateTime now = LocalDateTime.now();

        chatRepository.markAsRead(chatId, user.userId(), now);

        if (DIRECT_TYPES.contains(access.chat().getType())) {
            bucketRepository.markDirectMessagesRead(chatId, user.userId());
            eventPublisher.publish(ChatEventType.STATUS_UPDATE, chatId, null, user.userId(),
                    Map.of("readAt", now));
        }
    }

    private Chat createDirect(ChatType type, String directKey, UserContext user, UserInfo target) {
        LocalDateTime now = LocalDateTime.now();
        Chat chat = Chat.builder()
                .companyId(user.companyId())
                .type(type)
                .directKey(directKey)
                .participants(new ArrayList<>(List.of(
                        participant(user.userId(), ParticipantRole.FOREMAN, now),
                        participant(target.id(), target.role(), now))))
                .createdAt(now)
                .lastMessageAt(now)   // щоб новий чат не провалився в кінець списку
                .build();
        try {
            return chatRepository.save(chat);
        } catch (DuplicateKeyException e) {
            // гонка: двоє створили одночасно, унікальний індекс directKey нас врятував
            return chatRepository.findByDirectKey(directKey).orElseThrow(() -> e);
        }
    }

    private ChatResponse toResponse(Chat chat, String userId) {
        int unread = ChatAccessService.activeParticipant(chat, userId)
                .map(p -> {
                    LocalDateTime after = p.getLastReadAt() != null ? p.getLastReadAt() : p.getJoinedAt();
                    return (int) bucketRepository.countUnread(chat.getId(), userId, after);
                })
                .orElse(0);
        return chatMapper.toResponse(chat, unread);
    }

    static String directKey(ChatType type, String userA, String userB) {
        String first = userA.compareTo(userB) <= 0 ? userA : userB;
        String second = first.equals(userA) ? userB : userA;
        return type.name() + ":" + first + "_" + second;
    }

    private static ChatParticipant participant(String userId, ParticipantRole role, LocalDateTime now) {
        return ChatParticipant.builder().userId(userId).role(role).joinedAt(now).build();
    }
}
