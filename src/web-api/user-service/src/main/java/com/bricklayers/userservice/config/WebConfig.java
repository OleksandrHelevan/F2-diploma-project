package com.bricklayers.userservice.config;

import com.bricklayers.userservice.security.CurrentUserIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/*
  @author   user
  @project   F2-diploma-project
  @class  WebConfig
  @version  1.0.0 
  @since 08.10.2026 - 23.43
*/
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {
    private final CurrentUserIdArgumentResolver currentUserIdResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserIdResolver);
    }
}