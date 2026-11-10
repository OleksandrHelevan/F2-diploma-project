package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.service.relay.RedisChatEventRelay;
import com.bricklayers.common.instance.InstanceId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  RedisEventRelayConfig
  @version  1.0.0
  @since 03.11.2026 - 15.00
*/
@Slf4j
@Configuration
@ConditionalOnProperty(name = "chat.events.relay", havingValue = "redis")
public class RedisEventRelayConfig {

    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

    @Bean
    RedisChatEventRelay redisChatEventRelay(StringRedisTemplate redis, SimpMessagingTemplate messagingTemplate,
                                            JsonMapper json, InstanceId instanceId) {
        return new RedisChatEventRelay(redis, messagingTemplate, json, instanceId);
    }

    @Bean
    RedisMessageListenerContainer chatEventListenerContainer(RedisConnectionFactory connectionFactory,
                                                             RedisChatEventRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(RedisChatEventRelay.CHANNEL));

        container.setAutoStartup(false);
        return container;
    }

    @Bean
    ApplicationListener<ApplicationReadyEvent> subscribeWhenRedisIsAvailable(
            @Qualifier("chatEventListenerContainer") RedisMessageListenerContainer container) {
        return event -> Thread.ofVirtual().name("chat-event-subscriber").start(() -> {
            Duration delay = Duration.ofSeconds(1);
            while (!container.isRunning()) {
                try {
                    container.start();
                    log.info("Subscribed to Redis channel {}", RedisChatEventRelay.CHANNEL);
                } catch (RuntimeException exception) {
                    log.warn("Redis channel {} unavailable, retrying in {}s: {}",
                            RedisChatEventRelay.CHANNEL, delay.toSeconds(), exception.getMessage());
                    container.stop();
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    delay = delay.multipliedBy(2).compareTo(MAX_RETRY_DELAY) > 0 ? MAX_RETRY_DELAY : delay.multipliedBy(2);
                }
            }
        });
    }
}
