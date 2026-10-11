package com.bricklayers.chatservice.service;

/*
  @author   user
  @project   F2-diploma-project
  @class  MessageService
  @version  1.0.0 
  @since 06.10.2026 - 22.12
*/
import com.bricklayers.chatservice.dto.EditMessageRequest;
import com.bricklayers.chatservice.dto.SendMessageRequest;
import com.bricklayers.chatservice.dto.MessagePageResponse;
import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.entity.*;
import com.bricklayers.chatservice.exception.*;
import com.bricklayers.chatservice.mapper.MessageMapper;
import com.bricklayers.chatservice.repository.ChatBucketRepository;
import com.bricklayers.chatservice.repository.ChatRepository;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService.ChatAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MessageService {

    private static final int BUCKETS_PER_PAGE = 2;
    private static final int PREVIEW_LENGTH = 100;
    private static final Set<ChatType> DIRECT_TYPES =
            EnumSet.of(ChatType.FOREMAN_BUILDER, ChatType.FOREMAN_SUPPLIER);

    private final ChatRepository chatRepository;
    private final ChatBucketRepository bucketRepository;
    private final ChatAccessService accessService;
    private final MessageMapper messageMapper;
    private final ChatEventPublisher eventPublisher;


    public MessageResponse sendMessage(String chatId, SendMessageRequest request, UserContext user) {
        ChatAccess access = accessService.requireWriteAccess(chatId, user);
        Chat chat = access.chat();

        boolean noText = request.content() == null || request.content().isBlank();
        boolean noFiles = request.attachments() == null || request.attachments().isEmpty();
        if (noText && noFiles) {
            throw new InvalidChatOperationException("Повідомлення не може бути порожнім");
        }

        String threadId = normalize(request.threadId());
        if (threadId != null) {
            ChatMessage parent = loadMessage(chatId, null, threadId);
            if (parent.getType() == MessageType.SYSTEM) {
                throw new InvalidChatOperationException("Під системним повідомленням не можна відкрити тред");
            }
        }

        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = messageMapper.toNewMessage(request, user.userId(), user.name(), now);
        applyRecipient(chat, user.userId(), message);

        bucketRepository.appendMessage(chatId, threadId, message);

        MessageResponse response = messageMapper.toResponse(message, user.userId());
        MessageResponse broadcast = messageMapper.toResponse(message, null);

        if (threadId == null) {
            chatRepository.updateLastMessage(chatId, preview(message), now);
            eventPublisher.publish(ChatEventType.NEW_MESSAGE, chatId, null, user.userId(), broadcast);
        } else {
            bucketRepository.registerThreadReply(chatId, threadId, user.userId(), now);
            eventPublisher.publish(ChatEventType.NEW_MESSAGE, chatId, threadId, user.userId(), broadcast);
            publishThreadUpdate(chatId, threadId, user.userId());
        }
        return response;
    }


    public MessagePageResponse getMessages(String chatId, String threadId, int page, UserContext user) {
        accessService.requireReadAccess(chatId, user);

        Pageable pageable = PageRequest.of(Math.max(page, 0), BUCKETS_PER_PAGE);
        String normalizedThread = normalize(threadId);

        Slice<ChatBucket> buckets = normalizedThread == null
                ? bucketRepository.findByChatIdAndThreadIdIsNullOrderByEndDateDesc(chatId, pageable)
                : bucketRepository.findByChatIdAndThreadIdOrderByEndDateDesc(chatId, normalizedThread, pageable);

        return messageMapper.toPage(buckets, user.userId());
    }

    public MessageResponse editMessage(String chatId, String threadId, String messageId,
                                       EditMessageRequest request, UserContext user) {
        accessService.requireWriteAccess(chatId, user);
        String tid = normalize(threadId);

        ChatMessage message = loadMessage(chatId, tid, messageId);
        if (!user.userId().equals(message.getSenderId())) {
            throw new ChatAccessDeniedException("Редагувати можна лише власні повідомлення");
        }
        if (message.isDeleted() || message.getType() != MessageType.TEXT) {
            throw new InvalidChatOperationException("Це повідомлення не можна редагувати");
        }

        LocalDateTime now = LocalDateTime.now();
        if (!bucketRepository.editMessage(chatId, tid, messageId, request.content(), now)) {
            throw new MessageNotFoundException(messageId);
        }

        message.setContent(request.content());
        message.setEditedAt(now);

        eventPublisher.publish(ChatEventType.MESSAGE_EDITED, chatId, tid, user.userId(),
                messageMapper.toResponse(message, null));
        return messageMapper.toResponse(message, user.userId());
    }

    public void deleteMessage(String chatId, String threadId, String messageId, UserContext user) {
        ChatAccess access = accessService.requireWriteAccess(chatId, user);
        String tid = normalize(threadId);

        ChatMessage message = loadMessage(chatId, tid, messageId);
        if (message.isDeleted()) {
            return;
        }

        boolean author = user.userId().equals(message.getSenderId());
        boolean foremanModerator = access.chat().getType() == ChatType.BRIGADE_ALL
                && access.participant().getRole() == ParticipantRole.FOREMAN;

        if (!author && !foremanModerator) {
            throw new ChatAccessDeniedException("Видалити можна лише власне повідомлення");
        }

        bucketRepository.softDeleteMessage(chatId, tid, messageId);
        eventPublisher.publish(ChatEventType.MESSAGE_DELETED, chatId, tid, user.userId(), messageId);
    }

    public MessageResponse setLike(String chatId, String threadId, String messageId,
                                   boolean liked, UserContext user) {
        accessService.requireWriteAccess(chatId, user);
        String tid = normalize(threadId);

        ChatMessage message = loadMessage(chatId, tid, messageId);
        if (message.isDeleted()) {
            throw new InvalidChatOperationException("Повідомлення видалено");
        }

        bucketRepository.setLike(chatId, tid, messageId, user.userId(), liked);

        ChatMessage updated = loadMessage(chatId, tid, messageId);
        eventPublisher.publish(ChatEventType.LIKE_UPDATE, chatId, tid, user.userId(),
                Map.of("messageId", messageId, "likesCount", updated.getLikedBy().size()));

        return messageMapper.toResponse(updated, user.userId());
    }


    public void markDelivered(String chatId, String threadId, String messageId, UserContext user) {
        accessService.requireReadAccess(chatId, user);
        String tid = normalize(threadId);

        ChatMessage message = loadMessage(chatId, tid, messageId);
        if (!user.userId().equals(message.getRecipientId())) {
            throw new ChatAccessDeniedException("Статус може змінювати лише отримувач");
        }
        if (message.getStatus() != MessageStatus.RECEIVED) {
            return;
        }

        bucketRepository.updateStatus(chatId, tid, messageId, MessageStatus.DELIVERED);
        eventPublisher.publish(ChatEventType.STATUS_UPDATE, chatId, tid, user.userId(),
                Map.of("messageId", messageId, "status", MessageStatus.DELIVERED));
    }


    private ChatMessage loadMessage(String chatId, String threadId, String messageId) {
        return bucketRepository.findMessage(chatId, threadId, messageId)
                .orElseThrow(() -> new MessageNotFoundException(messageId));
    }

    private void applyRecipient(Chat chat, String senderId, ChatMessage message) {
        if (DIRECT_TYPES.contains(chat.getType())) {
            String recipientId = chat.getParticipants().stream()
                    .filter(p -> p.getLeftAt() == null && !p.getUserId().equals(senderId))
                    .map(ChatParticipant::getUserId)
                    .findFirst()
                    .orElseThrow(() -> new InvalidChatOperationException("У чаті немає отримувача"));
            message.setRecipientId(recipientId);
            message.setStatus(MessageStatus.RECEIVED);
        } else {
            message.setRecipientId(null);
            message.setStatus(null);
        }
    }

    private void publishThreadUpdate(String chatId, String parentId, String userId) {
        bucketRepository.findMessage(chatId, null, parentId)
                .map(ChatMessage::getThread)
                .ifPresent(info -> eventPublisher.publish(ChatEventType.THREAD_REPLY, chatId, null, userId,
                        Map.of("parentMessageId", parentId, "thread", messageMapper.toResponse(info))));
    }

    private static String preview(ChatMessage message) {
        String text = message.getContent();
        if (text == null || text.isBlank()) {
            return "Вкладення";
        }
        return text.length() <= PREVIEW_LENGTH ? text : text.substring(0, PREVIEW_LENGTH) + "…";
    }

    private static String normalize(String threadId) {
        return threadId == null || threadId.isBlank() ? null : threadId;
    }
}