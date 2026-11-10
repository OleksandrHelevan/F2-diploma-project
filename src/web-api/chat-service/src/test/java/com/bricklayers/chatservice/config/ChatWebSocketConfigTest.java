package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.ChatIdentityService;
import com.bricklayers.chatservice.security.ChatPrincipal;
import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.service.ChatAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketConfigTest {

    private static final UserContext USER = new UserContext("u1", "User", "c1", ParticipantRole.BUILDER);

    @Mock
    private ChatAccessService accessService;

    @Mock
    private ChatIdentityService identityService;

    @Mock
    private JwtDecoder jwtDecoder;

    @Mock
    private MessageChannel channel;

    private ChatWebSocketConfig config;
    private ChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        config = new ChatWebSocketConfig(accessService, identityService, jwtDecoder);
        ChannelRegistration registration = new ChannelRegistration();
        config.configureClientInboundChannel(registration);
        List<ChannelInterceptor> interceptors = ReflectionTestUtils.invokeMethod(registration, "getInterceptors");
        assertThat(interceptors).hasSize(1);
        interceptor = interceptors.getFirst();
    }

    @Test
    void registersEndpointAndBroker() {
        StompEndpointRegistry endpoints = mock(StompEndpointRegistry.class, Answers.RETURNS_DEEP_STUBS);
        MessageBrokerRegistry broker = mock(MessageBrokerRegistry.class);

        config.registerStompEndpoints(endpoints);
        config.configureMessageBroker(broker);

        verify(endpoints).addEndpoint("/ws");
        verify(broker).enableSimpleBroker("/topic");
        verify(broker).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void messageWithoutStompHeadersPassesThrough() {
        Message<String> message = new GenericMessage<>("x");

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    @Test
    void connectWithBearerTokenResolvesPrincipal() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("kc").build();
        when(jwtDecoder.decode("token")).thenReturn(jwt);
        when(identityService.resolve(jwt)).thenReturn(USER);
        Message<byte[]> message = stomp(StompCommand.CONNECT, Map.of("Authorization", "bearer  token "), null, null);

        interceptor.preSend(message, channel);

        assertThat(accessor(message).getUser()).isEqualTo(new ChatPrincipal(USER));
    }

    @Test
    void connectWithoutBearerTokenIsUnauthorized() {
        Message<byte[]> noHeader = stomp(StompCommand.CONNECT, Map.of(), null, null);
        Message<byte[]> basic = stomp(StompCommand.CONNECT, Map.of("Authorization", "Basic abc"), null, null);

        assertStatus(() -> interceptor.preSend(noHeader, channel), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> interceptor.preSend(basic, channel), HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(jwtDecoder);
    }

    @Test
    void connectWithInvalidJwtIsUnauthorized() {
        when(jwtDecoder.decode("bad")).thenThrow(new BadJwtException("bad"));
        Message<byte[]> message = stomp(StompCommand.CONNECT, Map.of("Authorization", "Bearer bad"), null, null);

        assertStatus(() -> interceptor.preSend(message, channel), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void demoHeadersAreIgnoredWhenDemoDisabled() {
        Message<byte[]> message = stomp(StompCommand.CONNECT, Map.of("x-user-id", "u1"), null, null);

        assertStatus(() -> interceptor.preSend(message, channel), HttpStatus.UNAUTHORIZED);
    }

    @Test
    void demoConnectBuildsUserFromHeaders() {
        ReflectionTestUtils.setField(config, "demoEnabled", true);
        Message<byte[]> message = stomp(StompCommand.CONNECT, Map.of(
                "x-user-id", "u1",
                "x-user-name-base64", Base64.getEncoder().encodeToString("Іван".getBytes(StandardCharsets.UTF_8)),
                "x-company-id", "c1",
                "x-user-role", "FOREMAN"), null, null);

        interceptor.preSend(message, channel);

        assertThat(accessor(message).getUser())
                .isEqualTo(new ChatPrincipal(new UserContext("u1", "Іван", "c1", ParticipantRole.FOREMAN)));
    }

    @Test
    void demoConnectValidatesHeaders() {
        ReflectionTestUtils.setField(config, "demoEnabled", true);
        String name = Base64.getEncoder().encodeToString("n".getBytes(StandardCharsets.UTF_8));

        assertStatus(() -> interceptor.preSend(stomp(StompCommand.CONNECT, Map.of(
                "x-user-id", "u1", "x-user-name-base64", name, "x-company-id", " ", "x-user-role", "FOREMAN"),
                null, null), channel), HttpStatus.UNAUTHORIZED);
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.CONNECT, Map.of(
                "x-user-id", "u1", "x-user-name-base64", name, "x-company-id", "c1", "x-user-role", "KING"),
                null, null), channel), HttpStatus.BAD_REQUEST);
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.CONNECT, Map.of(
                "x-user-id", "u1", "x-user-name-base64", "%%%", "x-company-id", "c1", "x-user-role", "FOREMAN"),
                null, null), channel), HttpStatus.BAD_REQUEST);
    }

    @Test
    void subscribeToChatTopicChecksReadAccess() {
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, Map.of(), "/topic/chats/chat-1", USER), channel);
        interceptor.preSend(stomp(StompCommand.SUBSCRIBE, Map.of(), "/topic/chats/chat-2/threads/t1", USER), channel);

        verify(accessService).requireReadAccess("chat-1", USER);
        verify(accessService).requireReadAccess("chat-2", USER);
    }

    @Test
    void subscribeRejectsInvalidDestinations() {
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, Map.of(), "/queue/other", USER), channel),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, Map.of(), null, USER), channel),
                HttpStatus.FORBIDDEN);
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, Map.of(), "/topic/chats/c/x/y", USER), channel),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.SUBSCRIBE, Map.of(), "/topic/chats/chat-1", null), channel),
                HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(accessService);
    }

    @Test
    void sendRequiresConnectedPrincipal() {
        Message<byte[]> connected = stomp(StompCommand.SEND, Map.of(), "/app/chats/c/typing", USER);

        assertThat(interceptor.preSend(connected, channel)).isSameAs(connected);
        assertStatus(() -> interceptor.preSend(stomp(StompCommand.SEND, Map.of(), "/app/x", null), channel),
                HttpStatus.UNAUTHORIZED);
    }

    @Test
    void otherCommandsPassThrough() {
        Message<byte[]> message = stomp(StompCommand.DISCONNECT, Map.of(), null, null);

        assertThat(interceptor.preSend(message, channel)).isSameAs(message);
    }

    private static Message<byte[]> stomp(StompCommand command, Map<String, String> headers, String destination,
                                         UserContext user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        headers.forEach(accessor::setNativeHeader);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(new ChatPrincipal(user));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static StompHeaderAccessor accessor(Message<?> message) {
        return MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
    }

    private static void assertStatus(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status) {
        assertThatThrownBy(call)
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(status));
    }
}
