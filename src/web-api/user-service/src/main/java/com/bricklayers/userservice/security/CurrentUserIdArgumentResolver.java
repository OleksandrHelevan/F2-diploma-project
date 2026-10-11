package com.bricklayers.userservice.security;

import com.bricklayers.userservice.annotation.CurrentUserId;
import org.jspecify.annotations.NonNull;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Objects;
import java.util.UUID;

/*
  @author   user
  @project   F2-diploma-project
  @class  CurrentUserIdArgumentResolver
  @version  1.0.0 
  @since 08.10.2026 - 23.42
*/

@Component
public class CurrentUserIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUserId.class)
                && UUID.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(@NonNull MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  @NonNull NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AuthenticationCredentialsNotFoundException("Користувач не автентифікований");
        }
        try {
            return UUID.fromString(Objects.requireNonNull(jwtAuthentication.getToken().getSubject()));
        } catch (IllegalArgumentException exception) {
            throw new AuthenticationCredentialsNotFoundException(
                    "JWT subject is not a local user ID", exception);
        }
    }
}
