package com.bricklayers.userservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.common.notification.NotificationType;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class NotificationClientTest {

    private static final String URL = "http://notification-service:8086/api/v1/notifications";

    private MockRestServiceServer server;
    private NotificationClient client;
    private final NotificationRequest request = new NotificationRequest(UUID.randomUUID(), NotificationType.WELCOME,
            "worker@example.com", Map.of("username", "worker"));

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        KeycloakAdminClient tokens = mock(KeycloakAdminClient.class);
        when(tokens.getAccessToken()).thenReturn("service-token");
        client = new NotificationClient(builder, "http://notification-service:8086", tokens, 3, Duration.ofMillis(10));
    }

    @Test
    void postsTheRequestWithTheServiceToken_andTheIdAsIdempotencyKey() {
        server.expect(once(), requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer service-token"))
                .andExpect(jsonPath("$.id").value(request.id().toString()))
                .andExpect(jsonPath("$.type").value("WELCOME"))
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThatCode(() -> client.send(request)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void retriesA5xxWithTheSameId_untilAccepted() {
        server.expect(times(2), requestTo(URL))
                .andExpect(jsonPath("$.id").value(request.id().toString()))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.ACCEPTED));

        assertThatCode(() -> client.send(request)).doesNotThrowAnyException();
        server.verify();
    }

    @Test
    void givesUpAfterMaxAttempts_withAClearError() {
        server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThatThrownBy(() -> client.send(request))
                .isInstanceOf(NotificationClient.NotificationNotAcceptedException.class)
                .hasMessageContaining(request.id().toString())
                .hasMessageContaining("after 3 attempt(s)");
        server.verify();
    }

    @Test
    void doesNotRetryA4xx() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.send(request)).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        server.verify();
    }
}
