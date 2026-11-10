package com.bricklayers.common.notification;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequest_hasNoViolations() {
        NotificationRequest request = new NotificationRequest(UUID.randomUUID(), NotificationType.WELCOME,
                "worker@example.com", Map.of("username", "worker"));

        assertThat(validator.validate(request)).isEmpty();
        assertThat(NotificationType.valueOf("ACCOUNT_CHANGED")).isEqualTo(NotificationType.ACCOUNT_CHANGED);
    }

    @Test
    void missingIdTypeAndInvalidRecipient_areViolations() {
        NotificationRequest request = new NotificationRequest(null, null, "not-an-email", null);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("id", "type", "recipient");
    }
}
