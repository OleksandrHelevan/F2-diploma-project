package com.bricklayers.notificationservice.notification;

import com.bricklayers.common.notification.NotificationRequest;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationTemplates {

    public record Rendered(String subject, String body) {
    }

    public Rendered render(NotificationRequest request) {
        Map<String, String> params = request.params() == null ? Map.of() : request.params();
        return switch (request.type()) {
            case WELCOME -> new Rendered("Welcome to Bricklayers",
                    "Hello " + param(params, "displayName") + ",\n\nan account '" + param(params, "username")
                            + "' was created for you with the role " + param(params, "role") + ".\n");
            case ACCOUNT_CHANGED -> new Rendered("Your Bricklayers account was updated",
                    "Hello " + param(params, "displayName") + ",\n\nan administrator updated your account. "
                            + roleLine(params) + "\nAccount active: " + param(params, "active") + "\n");
        };
    }

    private static String roleLine(Map<String, String> params) {
        String role = param(params, "role");
        String previousRole = params.get("previousRole");
        return previousRole == null || previousRole.equals(role)
                ? "Your role is " + role + "."
                : "Your role changed from " + previousRole + " to " + role + ".";
    }

    private static String param(Map<String, String> params, String key) {
        String value = params.get(key);
        return value == null || value.isBlank() ? "-" : value;
    }
}
