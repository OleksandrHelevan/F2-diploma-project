package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.security.UserContext;
import com.bricklayers.chatservice.entity.ParticipantRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class UserContextArgumentResolver implements HandlerMethodArgumentResolver {

    private final boolean demoEnabled;

    public UserContextArgumentResolver(@Value("${chat.demo.enabled:false}") boolean demoEnabled) {
        this.demoEnabled = demoEnabled;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == UserContext.class;
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        if (!demoEnabled) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Chat demo identity headers are disabled");
        }

        String userId = requiredHeader(webRequest, "X-User-Id");
        String name = decodeName(requiredHeader(webRequest, "X-User-Name-Base64"));
        String companyId = requiredHeader(webRequest, "X-Company-Id");
        String role = requiredHeader(webRequest, "X-User-Role");
        try {
            return new UserContext(userId, name, companyId, ParticipantRole.valueOf(role));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-User-Role", exception);
        }
    }

    private static String requiredHeader(NativeWebRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing " + name);
        }
        return value;
    }

    private static String decodeName(String encodedName) {
        try {
            return new String(Base64.getDecoder().decode(encodedName), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid X-User-Name-Base64", exception);
        }
    }
}
