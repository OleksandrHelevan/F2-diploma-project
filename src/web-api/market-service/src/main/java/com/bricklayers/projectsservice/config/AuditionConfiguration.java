package com.bricklayers.projectsservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  AuditionConfiguration
  @version  1.0.0
  @since 01.11.2026 - 16.00
*/
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class AuditionConfiguration {

    @Bean
    public AuditorAware<String> auditorAware() {
        return new AuditorAwareImpl();
    }
}
