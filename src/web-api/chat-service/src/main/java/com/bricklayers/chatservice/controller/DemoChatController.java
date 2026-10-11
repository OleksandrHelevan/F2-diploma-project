package com.bricklayers.chatservice.controller;

import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatProvisioningService;
import com.bricklayers.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chats/demo")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "chat.demo.enabled", havingValue = "true")
public class DemoChatController {

    public static final String COMPANY_ID = "demo-company";
    public static final String FOREMAN_ID = "demo-foreman";
    public static final String BUILDER_ID = "demo-builder";

    private final ChatProvisioningService provisioningService;
    private final ChatService chatService;

    @PostMapping
    public ChatResponse ensureDemoChat(UserContext user) {
        if (!user.companyId().equals(COMPANY_ID)
                || !List.of(FOREMAN_ID, BUILDER_ID).contains(user.userId())
                || !isDemoRole(user.role())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN, "Only demo chat participants can use this endpoint");
        }
        Chat chat = provisioningService.createBrigadeChat(
                COMPANY_ID, "demo-brigade", "Демо-бригада",
                FOREMAN_ID, List.of(BUILDER_ID));
        return chatService.getChat(chat.getId(), user);
    }

    private static boolean isDemoRole(ParticipantRole role) {
        return role == ParticipantRole.FOREMAN || role == ParticipantRole.BUILDER;
    }
}
