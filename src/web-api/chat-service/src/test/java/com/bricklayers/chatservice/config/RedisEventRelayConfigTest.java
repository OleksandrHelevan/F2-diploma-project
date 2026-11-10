package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.service.relay.RedisChatEventRelay;
import com.bricklayers.common.instance.InstanceId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisEventRelayConfigTest {

    private final RedisEventRelayConfig config = new RedisEventRelayConfig();

    @Mock
    private RedisConnectionFactory connectionFactory;

    @Test
    void createsRelayAndListenerContainerWithoutAutoStartup() {
        RedisChatEventRelay relay = config.redisChatEventRelay(mock(StringRedisTemplate.class),
                mock(SimpMessagingTemplate.class), JsonMapper.builder().build(), new InstanceId("node-test"));

        RedisMessageListenerContainer container = config.chatEventListenerContainer(connectionFactory, relay);

        assertThat(relay).isNotNull();
        assertThat(container.getConnectionFactory()).isSameAs(connectionFactory);
        assertThat(container.isAutoStartup()).isFalse();
        assertThat(container.isRunning()).isFalse();
    }

    @Test
    void subscriberStartsContainerWhenRedisIsAvailable() {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(false, true);
        ApplicationListener<ApplicationReadyEvent> listener = config.subscribeWhenRedisIsAvailable(container);

        listener.onApplicationEvent(mock(ApplicationReadyEvent.class));

        verify(container, timeout(2000)).start();
        verify(container, timeout(2000).times(2)).isRunning();
    }

    @Test
    void subscriberRetriesAfterRedisFailure() {
        RedisMessageListenerContainer container = mock(RedisMessageListenerContainer.class);
        when(container.isRunning()).thenReturn(false, false, true);
        doThrow(new RedisConnectionFailureException("down")).doNothing().when(container).start();
        ApplicationListener<ApplicationReadyEvent> listener = config.subscribeWhenRedisIsAvailable(container);

        listener.onApplicationEvent(mock(ApplicationReadyEvent.class));

        verify(container, timeout(5000).times(2)).start();
        verify(container, timeout(5000)).stop();
    }
}
