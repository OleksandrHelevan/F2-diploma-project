package com.bricklayers.common.instance;

import java.util.UUID;

public record InstanceId(String value) {

    private static final String ALLOWED = "[A-Za-z0-9._-]{1,64}";

    public InstanceId {
        if (value == null || !value.matches(ALLOWED)) {
            throw new IllegalArgumentException(
                    "INSTANCE_ID must contain 1-64 letters, digits, dots, underscores or hyphens");
        }
    }

    public static InstanceId of(String configured) {
        if (configured == null || configured.isBlank()) {
            return new InstanceId("node-" + UUID.randomUUID().toString().substring(0, 8));
        }
        return new InstanceId(configured.trim());
    }

    @Override
    public String toString() {
        return value;
    }
}
