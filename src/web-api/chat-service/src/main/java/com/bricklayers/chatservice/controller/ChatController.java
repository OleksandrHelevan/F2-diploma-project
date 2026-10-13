package com.bricklayers.chatservice.controller;

import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.dto.CreateDirectChatRequest;
import com.bricklayers.chatservice.dto.EditMessageRequest;
import com.bricklayers.chatservice.dto.MessagePageResponse;
import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.dto.SendMessageRequest;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatService;
import com.bricklayers.chatservice.service.MessageService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chats")
@SecurityRequirement(name = "keycloak", scopes = {"openid", "profile", "email"})
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final MessageService messageService;

    @GetMapping
    public Page<ChatResponse> getMyChats(
            UserContext user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "page must be non-negative and size must be between 1 and 100");
        }
        return chatService.getMyChats(user, PageRequest.of(page, size));
    }

    @PostMapping("/direct")
    @ResponseStatus(HttpStatus.CREATED)
    public ChatResponse createDirectChat(@Valid @RequestBody CreateDirectChatRequest request, UserContext user) {
        return chatService.createDirectChat(request, user);
    }

    @GetMapping("/{chatId}")
    public ChatResponse getChat(@PathVariable String chatId, UserContext user) {
        return chatService.getChat(chatId, user);
    }

    @GetMapping("/{chatId}/messages")
    public MessagePageResponse getMessages(
            @PathVariable String chatId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) String threadId,
            UserContext user
    ) {
        if (page < 0) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be non-negative");
        }
        return messageService.getMessages(chatId, threadId, page, user);
    }

    @PostMapping("/{chatId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse sendMessage(
            @PathVariable String chatId,
            @Valid @RequestBody SendMessageRequest request,
            UserContext user
    ) {
        return messageService.sendMessage(chatId, request, user);
    }

    @PatchMapping("/{chatId}/messages/{messageId}")
    public MessageResponse editMessage(
            @PathVariable String chatId,
            @PathVariable String messageId,
            @RequestParam(required = false) String threadId,
            @Valid @RequestBody EditMessageRequest request,
            UserContext user
    ) {
        return messageService.editMessage(chatId, threadId, messageId, request, user);
    }

    @DeleteMapping("/{chatId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMessage(
            @PathVariable String chatId,
            @PathVariable String messageId,
            @RequestParam(required = false) String threadId,
            UserContext user
    ) {
        messageService.deleteMessage(chatId, threadId, messageId, user);
    }

    @PostMapping("/{chatId}/messages/{messageId}/like")
    public MessageResponse likeMessage(
            @PathVariable String chatId,
            @PathVariable String messageId,
            @RequestParam(required = false) String threadId,
            @RequestParam boolean liked,
            UserContext user
    ) {
        return messageService.setLike(chatId, threadId, messageId, liked, user);
    }

    @PostMapping("/{chatId}/messages/{messageId}/delivered")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markDelivered(
            @PathVariable String chatId,
            @PathVariable String messageId,
            @RequestParam(required = false) String threadId,
            UserContext user
    ) {
        messageService.markDelivered(chatId, threadId, messageId, user);
    }

    @PostMapping("/{chatId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAsRead(@PathVariable String chatId, UserContext user) {
        chatService.markAsRead(chatId, user);
    }
}
