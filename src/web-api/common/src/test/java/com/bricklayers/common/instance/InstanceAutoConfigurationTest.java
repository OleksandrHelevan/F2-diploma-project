package com.bricklayers.common.instance;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;

class InstanceAutoConfigurationTest {

    private final InstanceAutoConfiguration configuration = new InstanceAutoConfiguration();

    @Test
    void instanceIdUsesTheConfiguredValue() {
        assertThat(configuration.instanceId("user-7").value()).isEqualTo("user-7");
    }

    @Test
    void instanceIdFilterRunsFirst() {
        FilterRegistrationBean<InstanceIdFilter> registration = configuration.instanceIdFilter(InstanceId.of("user-7"));

        assertThat(registration.getFilter()).isInstanceOf(InstanceIdFilter.class);
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
    }

    @Test
    void labLatencyFilterRunsRightAfterTheInstanceIdFilter() {
        FilterRegistrationBean<LabLatencyFilter> registration =
                configuration.labLatencyFilter(InstanceId.of("user-7"), 300);

        assertThat(registration.getFilter()).isInstanceOf(LabLatencyFilter.class);
        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
