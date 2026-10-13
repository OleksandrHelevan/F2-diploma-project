package com.bricklayers.chatservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI chatServiceOpenApi(@Value("${keycloak.issuer-uri}") String issuerUri) {
        return new OpenAPI()
                .info(new Info()
                        .title("Bricklayers Chat Service API")
                        .version("v1")
                        .description("Chat API authenticated with Keycloak access tokens"))
                .components(new Components().addSecuritySchemes("keycloak",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.OAUTH2)
                                .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                                        .authorizationUrl(issuerUri + "/protocol/openid-connect/auth")
                                        .tokenUrl(issuerUri + "/protocol/openid-connect/token")
                                        .scopes(new Scopes()
                                                .addString("openid", "Authenticate users")
                                                .addString("profile", "Read user profile")
                                                .addString("email", "Read email address"))))))
                .addSecurityItem(new SecurityRequirement()
                        .addList("keycloak", List.of("openid", "profile", "email")));
    }
}
