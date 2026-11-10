package com.bricklayers.userservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Slf4j
@Component
public class NotificationClient {

    static final String PATH = "/api/v1/notifications";

    private final RestClient restClient;
    private final KeycloakAdminClient tokens;
    private final int maxAttempts;
    private final Duration backoff;

    @Autowired
    public NotificationClient(@Value("${app.notifications.service-url}") String serviceUrl,
                              KeycloakAdminClient tokens,
                              @Value("${app.notifications.max-attempts:3}") int maxAttempts,
                              @Value("${app.notifications.retry-backoff:300ms}") Duration backoff) {
        this(RestClient.builder().requestFactory(requestFactory()), serviceUrl, tokens, maxAttempts, backoff);
    }

    NotificationClient(RestClient.Builder builder, String serviceUrl, KeycloakAdminClient tokens,
                       int maxAttempts, Duration backoff) {
        this.restClient = builder.baseUrl(serviceUrl).build();
        this.tokens = tokens;
        this.maxAttempts = maxAttempts;
        this.backoff = backoff;
    }

    public void send(NotificationRequest request) {
        send(request, maxAttempts);
    }

    public void send(NotificationRequest request, int attempts) {
        RestClientException lastFailure = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                post(request);
                log.info("Notification {} ({}) for {} accepted by notification-service (attempt {}) on {}",
                        request.id(), request.type(), request.recipient(), attempt, Thread.currentThread().getName());
                return;
            } catch (HttpClientErrorException clientError) {
                throw clientError;
            } catch (RestClientException failure) {
                lastFailure = failure;
                log.warn("Notification {} attempt {}/{} failed: {}", request.id(), attempt, attempts, failure.getMessage());
                if (attempt < attempts) {
                    pause(backoff.multipliedBy(attempt));
                }
            }
        }
        throw new NotificationNotAcceptedException(request, attempts, lastFailure);
    }

    private void post(NotificationRequest request) {
        String accessToken = tokens.getAccessToken();
        restClient.post()
                .uri(PATH)
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(3));
        return factory;
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying a notification", exception);
        }
    }

    public static class NotificationNotAcceptedException extends RuntimeException {
        public NotificationNotAcceptedException(NotificationRequest request, int attempts, Throwable cause) {
            super("notification-service did not accept notification " + request.id() + " (" + request.type()
                    + " for " + request.recipient() + ") after " + attempts + " attempt(s)", cause);
        }
    }
}
