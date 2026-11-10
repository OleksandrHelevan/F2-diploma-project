package com.bricklayers.chatservice.service.relay;

import com.bricklayers.chatservice.dto.ChatEventResponse;
import com.bricklayers.chatservice.entity.ChatEventType;
import com.bricklayers.common.instance.InstanceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RedisChatEventRelayTest {

    private static final String DESTINATION = "/topic/chats/chat-1";

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private final JsonMapper json = JsonMapper.builder().build();

    private final ChatEventResponse event = new ChatEventResponse(
            ChatEventType.TYPING, "chat-1", null, "user-1", Map.of("text", "привіт"),
            LocalDateTime.of(2026, 11, 3, 12, 0));

    private RedisChatEventRelay relay(String instance) {
        return new RedisChatEventRelay(redis, messagingTemplate, json, InstanceId.of(instance));
    }

    @Test
    void publishesToRedisInsteadOfDeliveringLocally() {
        relay("chat-a").relay(DESTINATION, event);

        ArgumentCaptor<String> published = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(RedisChatEventRelay.CHANNEL), published.capture());
        JsonNode envelope = json.readTree(published.getValue());
        assertThat(envelope.path("origin").asString()).isEqualTo("chat-a");
        assertThat(envelope.path("destination").asString()).isEqualTo(DESTINATION);
        assertThat(envelope.path("event").path("type").asString()).isEqualTo("TYPING");
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void messageFromAnotherReplicaIsDeliveredToLocalSubscribers() {
        relay("chat-a").relay(DESTINATION, event);
        ArgumentCaptor<String> published = ArgumentCaptor.forClass(String.class);
        verify(redis).convertAndSend(eq(RedisChatEventRelay.CHANNEL), published.capture());

        relay("chat-b").onMessage(new DefaultMessage(
                RedisChatEventRelay.CHANNEL.getBytes(StandardCharsets.UTF_8),
                published.getValue().getBytes(StandardCharsets.UTF_8)), null);

        ArgumentCaptor<Object> delivered = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(eq(DESTINATION), delivered.capture());
        JsonNode payload = (JsonNode) delivered.getValue();
        assertThat(payload.path("chatId").asString()).isEqualTo("chat-1");
        assertThat(payload.path("payload").path("text").asString()).isEqualTo("привіт");
    }

    @Test
    void redisFailureFallsBackToLocalDelivery() {
        doThrow(new RedisConnectionFailureException("down"))
                .when(redis).convertAndSend(anyString(), anyString());

        relay("chat-a").relay(DESTINATION, event);

        verify(messagingTemplate).convertAndSend(DESTINATION, event);
    }

    @Test
    void malformedMessageIsDropped() {
        relay("chat-b").onMessage(new DefaultMessage(
                RedisChatEventRelay.CHANNEL.getBytes(StandardCharsets.UTF_8),
                "not json".getBytes(StandardCharsets.UTF_8)), null);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }
}
