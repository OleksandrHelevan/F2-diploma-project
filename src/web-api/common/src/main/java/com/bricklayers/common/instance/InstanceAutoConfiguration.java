package com.bricklayers.common.instance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class InstanceAutoConfiguration {

    @Bean
    InstanceId instanceId(@Value("${app.instance-id:${INSTANCE_ID:${HOSTNAME:}}}") String configured) {
        return InstanceId.of(configured);
    }

    @Bean
    FilterRegistrationBean<InstanceIdFilter> instanceIdFilter(InstanceId instanceId) {
        FilterRegistrationBean<InstanceIdFilter> registration = new FilterRegistrationBean<>(new InstanceIdFilter(instanceId));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    @Profile("lab3")
    FilterRegistrationBean<LabLatencyFilter> labLatencyFilter(
            InstanceId instanceId,
            @Value("${app.lab.slow-delay-ms:500}") long delayMillis) {
        FilterRegistrationBean<LabLatencyFilter> registration =
                new FilterRegistrationBean<>(new LabLatencyFilter(instanceId, delayMillis));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
