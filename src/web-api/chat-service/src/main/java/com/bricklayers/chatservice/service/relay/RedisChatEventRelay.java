package com.bricklayers.chatservice.service.relay;

import com.bricklayers.chatservice.dto.ChatEventResponse;
import com.bricklayers.common.instance.InstanceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  RedisChatEventRelay
  @version  1.0.0
  @since 03.11.2026 - 15.00
*/

@Slf4j
public class RedisChatEventRelay implements ChatEventRelay, MessageListener {

    public static final String CHANNEL = "chat:events:v1";

    private final StringRedisTemplate redis;
    private final SimpMessagingTemplate messagingTemplate;
    private final JsonMapper json;
    private final InstanceId instanceId;

    public RedisChatEventRelay(StringRedisTemplate redis, SimpMessagingTemplate messagingTemplate,
                               JsonMapper json, InstanceId instanceId) {
        this.redis = redis;
        this.messagingTemplate = messagingTemplate;
        this.json = json;
        this.instanceId = instanceId;
    }

    record Envelope(String origin, String destination, JsonNode event) {
    }

    @Override
    public void relay(String destination, ChatEventResponse event) {
        String message;
        try {
            message = json.writeValueAsString(new Envelope(instanceId.value(), destination, json.valueToTree(event)));
        } catch (RuntimeException exception) {
            log.error("Cannot serialize chat event {} for {}", event.type(), destination, exception);
            return;
        }
        try {
            redis.convertAndSend(CHANNEL, message);
        } catch (RuntimeException exception) {

            log.warn("Redis unavailable, delivering chat event {} to local subscribers only", event.type());
            messagingTemplate.convertAndSend(destination, event);
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            Envelope envelope = json.readValue(new String(message.getBody(), StandardCharsets.UTF_8), Envelope.class);
            messagingTemplate.convertAndSend(envelope.destination(), envelope.event());
            log.info("Delivered chat event {} to {} on {} (published by {})",
                    envelope.event().path("type").asString(), envelope.destination(), instanceId, envelope.origin());
        } catch (RuntimeException exception) {
            log.error("Dropping malformed chat event from Redis channel {}", CHANNEL, exception);
        }
    }
}
