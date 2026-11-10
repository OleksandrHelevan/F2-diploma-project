package com.bricklayers.chatservice.service;

import com.bricklayers.chatservice.dto.ChatEventResponse;
import com.bricklayers.chatservice.dto.TypingRequest;
import com.bricklayers.chatservice.entity.ChatEventType;
import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.relay.ChatEventRelay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatEventPublisherAndTypingServiceTest {

    private static final UserContext USER = new UserContext("u1", "User One", "c1", ParticipantRole.BUILDER);

    @Mock
    private ChatEventRelay eventRelay;

    @Mock
    private ChatAccessService accessService;

    @Mock
    private ChatEventPublisher mockedPublisher;

    @Test
    void publishToChatTopicWhenNoThread() {
        ChatEventPublisher publisher = new ChatEventPublisher(eventRelay);

        publisher.publish(ChatEventType.NEW_MESSAGE, "chat-1", null, "u1", "payload");

        ArgumentCaptor<ChatEventResponse> captor = ArgumentCaptor.forClass(ChatEventResponse.class);
        verify(eventRelay).relay(eq("/topic/chats/chat-1"), captor.capture());
        ChatEventResponse event = captor.getValue();
        assertThat(event.type()).isEqualTo(ChatEventType.NEW_MESSAGE);
        assertThat(event.chatId()).isEqualTo("chat-1");
        assertThat(event.threadId()).isNull();
        assertThat(event.userId()).isEqualTo("u1");
        assertThat(event.payload()).isEqualTo("payload");
        assertThat(event.at()).isNotNull();
    }

    @Test
    void publishToThreadTopicWhenThreadGiven() {
        ChatEventPublisher publisher = new ChatEventPublisher(eventRelay);

        publisher.publish(ChatEventType.TYPING, "chat-1", "t1", "u1", null);

        ArgumentCaptor<ChatEventResponse> captor = ArgumentCaptor.forClass(ChatEventResponse.class);
        verify(eventRelay).relay(eq("/topic/chats/chat-1/threads/t1"), captor.capture());
        assertThat(captor.getValue().threadId()).isEqualTo("t1");
    }

    @Test
    void typingStartedInMainChatPublishesTypingEvent() {
        TypingService typingService = new TypingService(accessService, mockedPublisher);

        typingService.notifyTyping("chat-1", new TypingRequest(" ", true), USER);

        verify(accessService).requireWriteAccess("chat-1", USER);
        verify(mockedPublisher).publish(ChatEventType.TYPING, "chat-1", null, "u1", "User One");
    }

    @Test
    void typingStoppedInThreadPublishesStoppedEvent() {
        TypingService typingService = new TypingService(accessService, mockedPublisher);

        typingService.notifyTyping("chat-1", new TypingRequest("t1", false), USER);

        verify(mockedPublisher).publish(ChatEventType.STOPPED_TYPING, "chat-1", "t1", "u1", "User One");
    }
}
