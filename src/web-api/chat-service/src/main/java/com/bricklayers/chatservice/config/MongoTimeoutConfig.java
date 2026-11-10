package com.bricklayers.chatservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/*
  @author   oleksandr
  @project   F2-diploma-project
  @class  MongoTimeoutConfig
  @version  1.0.0
  @since 04.11.2026 - 12.00
*/

@Configuration
public class MongoTimeoutConfig {

    @Bean
    MongoClientSettingsBuilderCustomizer mongoFailFast(
            @Value("${chat.mongo.server-selection-timeout:5s}") Duration serverSelectionTimeout) {
        return settings -> settings.applyToClusterSettings(cluster ->
                cluster.serverSelectionTimeout(serverSelectionTimeout.toMillis(), TimeUnit.MILLISECONDS));
    }
}
