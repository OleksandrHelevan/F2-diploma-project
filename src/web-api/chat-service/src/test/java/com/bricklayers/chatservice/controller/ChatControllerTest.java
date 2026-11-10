package com.bricklayers.chatservice.controller;

import com.bricklayers.chatservice.dto.ChatResponse;
import com.bricklayers.chatservice.dto.CreateDirectChatRequest;
import com.bricklayers.chatservice.dto.EditMessageRequest;
import com.bricklayers.chatservice.dto.MessagePageResponse;
import com.bricklayers.chatservice.dto.MessageResponse;
import com.bricklayers.chatservice.dto.SendMessageRequest;
import com.bricklayers.chatservice.dto.TypingRequest;
import com.bricklayers.chatservice.entity.Chat;
import com.bricklayers.chatservice.entity.ChatType;
import com.bricklayers.chatservice.entity.MessageType;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.ChatPrincipal;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatProvisioningService;
import com.bricklayers.chatservice.service.ChatService;
import com.bricklayers.chatservice.service.MessageService;
import com.bricklayers.chatservice.service.TypingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    private static final UserContext USER = new UserContext("u1", "User", "c1", ParticipantRole.FOREMAN);

    @Mock
    private ChatService chatService;

    @Mock
    private MessageService messageService;

    @Mock
    private ChatProvisioningService provisioningService;

    @Mock
    private TypingService typingService;

    @InjectMocks
    private ChatController controller;

    @Test
    void getMyChatsDelegatesWithPageRequest() {
        Page<ChatResponse> page = new PageImpl<>(List.of());
        when(chatService.getMyChats(USER, PageRequest.of(1, 20))).thenReturn(page);

        assertThat(controller.getMyChats(USER, 1, 20)).isSameAs(page);
    }

    @Test
    void getMyChatsRejectsInvalidPaging() {
        assertThatThrownBy(() -> controller.getMyChats(USER, -1, 10)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.getMyChats(USER, 0, 0)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.getMyChats(USER, 0, 101))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(chatService);
    }

    @Test
    void chatEndpointsDelegateToChatService() {
        ChatResponse response = chatResponse("chat-1");
        CreateDirectChatRequest request = new CreateDirectChatRequest(ChatType.FOREMAN_BUILDER, "b1");
        when(chatService.createDirectChat(request, USER)).thenReturn(response);
        when(chatService.getChat("chat-1", USER)).thenReturn(response);

        assertThat(controller.createDirectChat(request, USER)).isSameAs(response);
        assertThat(controller.getChat("chat-1", USER)).isSameAs(response);
        controller.markAsRead("chat-1", USER);
        verify(chatService).markAsRead("chat-1", USER);
    }

    @Test
    void getMessagesValidatesPageAndDelegates() {
        MessagePageResponse page = new MessagePageResponse(List.of(), false, 0);
        when(messageService.getMessages("chat-1", "t1", 0, USER)).thenReturn(page);

        assertThat(controller.getMessages("chat-1", 0, "t1", USER)).isSameAs(page);
        assertThatThrownBy(() -> controller.getMessages("chat-1", -1, null, USER))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void messageEndpointsDelegateToMessageService() {
        MessageResponse response = new MessageResponse("m1", "u1", "User", MessageType.TEXT, "x", null, null,
                false, null, null, 0, false, List.of(), null);
        SendMessageRequest send = new SendMessageRequest("x", null, null, null);
        EditMessageRequest edit = new EditMessageRequest("y");
        when(messageService.sendMessage("chat-1", send, USER)).thenReturn(response);
        when(messageService.editMessage("chat-1", "t1", "m1", edit, USER)).thenReturn(response);
        when(messageService.setLike("chat-1", null, "m1", true, USER)).thenReturn(response);

        assertThat(controller.sendMessage("chat-1", send, USER)).isSameAs(response);
        assertThat(controller.editMessage("chat-1", "m1", "t1", edit, USER)).isSameAs(response);
        assertThat(controller.likeMessage("chat-1", "m1", null, true, USER)).isSameAs(response);
        controller.deleteMessage("chat-1", "m1", "t1", USER);
        controller.markDelivered("chat-1", "m1", null, USER);

        verify(messageService).deleteMessage("chat-1", "t1", "m1", USER);
        verify(messageService).markDelivered("chat-1", null, "m1", USER);
    }

    @Test
    void demoControllerProvisionsBrigadeChatForDemoForeman() {
        DemoChatController demo = new DemoChatController(provisioningService, chatService);
        UserContext demoUser = new UserContext(DemoChatController.FOREMAN_ID, "F", DemoChatController.COMPANY_ID,
                ParticipantRole.FOREMAN);
        when(provisioningService.createBrigadeChat(DemoChatController.COMPANY_ID, "demo-brigade", "Демо-бригада",
                DemoChatController.FOREMAN_ID, List.of(DemoChatController.BUILDER_ID)))
                .thenReturn(Chat.builder().id("demo-chat").build());
        ChatResponse response = chatResponse("demo-chat");
        when(chatService.getChat("demo-chat", demoUser)).thenReturn(response);

        assertThat(demo.ensureDemoChat(demoUser)).isSameAs(response);
    }

    @Test
    void demoControllerRejectsNonDemoUsers() {
        DemoChatController demo = new DemoChatController(provisioningService, chatService);

        assertThatThrownBy(() -> demo.ensureDemoChat(new UserContext(DemoChatController.FOREMAN_ID, "F", "other",
                ParticipantRole.FOREMAN))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> demo.ensureDemoChat(new UserContext("stranger", "S", DemoChatController.COMPANY_ID,
                ParticipantRole.FOREMAN))).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> demo.ensureDemoChat(new UserContext(DemoChatController.BUILDER_ID, "B",
                DemoChatController.COMPANY_ID, ParticipantRole.CUSTOMER)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(provisioningService);
    }

    @Test
    void webSocketTypingRequiresChatPrincipal() {
        ChatWebSocketController wsController = new ChatWebSocketController(typingService);
        TypingRequest request = new TypingRequest(null, true);

        wsController.typing("chat-1", request, new ChatPrincipal(USER));
        verify(typingService).notifyTyping("chat-1", request, USER);

        Principal anonymous = () -> "anon";
        assertThatThrownBy(() -> wsController.typing("chat-1", request, anonymous))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> wsController.typing("chat-1", request, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void chatPrincipalNameIsUserId() {
        assertThat(new ChatPrincipal(USER).getName()).isEqualTo("u1");
    }

    private static ChatResponse chatResponse(String id) {
        return new ChatResponse(id, ChatType.BRIGADE_ALL, null, null, null, List.of(), null, null, 0, false);
    }
}
