package com.bricklayers.common.instance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InstanceIdTest {

    @Test
    void trimsTheConfiguredValue() {
        assertThat(InstanceId.of("  user-1  ").value()).isEqualTo("user-1");
        assertThat(InstanceId.of("user-1")).hasToString("user-1");
    }

    @Test
    void blankValue_generatesARandomNodeId() {
        InstanceId first = InstanceId.of("  ");
        InstanceId second = InstanceId.of(null);

        assertThat(first.value()).matches("node-[0-9a-f]{8}");
        assertThat(second.value()).matches("node-[0-9a-f]{8}");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void rejectsValuesThatAreUnsafeForAHeader() {
        assertThatThrownBy(() -> InstanceId.of("bad id\r\nX-Injected: 1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceId("x".repeat(65)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InstanceId(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
