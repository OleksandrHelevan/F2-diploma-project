package com.bricklayers.notificationservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import com.bricklayers.common.notification.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTemplatesTest {

    private final NotificationTemplates templates = new NotificationTemplates();

    @Test
    void welcome() {
        NotificationTemplates.Rendered rendered = templates.render(request(NotificationType.WELCOME,
                Map.of("username", "ivan", "displayName", "Ivan Petrenko", "role", "BUILDER")));

        assertThat(rendered.subject()).isEqualTo("Welcome to Bricklayers");
        assertThat(rendered.body()).isEqualTo(
                "Hello Ivan Petrenko,\n\nan account 'ivan' was created for you with the role BUILDER.\n");
    }

    @Test
    void accountChanged_withRoleChange() {
        NotificationTemplates.Rendered rendered = templates.render(request(NotificationType.ACCOUNT_CHANGED,
                Map.of("displayName", "Ivan", "role", "FOREMAN", "previousRole", "BUILDER", "active", "true")));

        assertThat(rendered.subject()).isEqualTo("Your Bricklayers account was updated");
        assertThat(rendered.body()).contains("Your role changed from BUILDER to FOREMAN.", "Account active: true");
    }

    @Test
    void accountChanged_sameRole_andMissingParamsRenderAsDash() {
        NotificationTemplates.Rendered rendered = templates.render(request(NotificationType.ACCOUNT_CHANGED,
                Map.of("role", "BUILDER", "previousRole", "BUILDER")));

        assertThat(rendered.body()).startsWith("Hello -,").contains("Your role is BUILDER.", "Account active: -");
    }

    private static NotificationRequest request(NotificationType type, Map<String, String> params) {
        return new NotificationRequest(UUID.randomUUID(), type, "ivan@example.com", params);
    }
}
