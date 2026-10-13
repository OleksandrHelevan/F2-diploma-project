package com.bricklayers.chatservice.config;

import com.bricklayers.chatservice.entity.ParticipantRole;
import com.bricklayers.chatservice.security.ChatIdentityService;
import com.bricklayers.chatservice.security.UserContext;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
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
    private final ChatIdentityService identityService;

    public UserContextArgumentResolver(
            @Value("${chat.demo.enabled:false}") boolean demoEnabled,
            @Lazy ChatIdentityService identityService
    ) {
        this.demoEnabled = demoEnabled;
        this.identityService = identityService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == UserContext.class;
    }

    @Override
    public Object resolveArgument(
            @NonNull MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            @NonNull NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory
    ) {
        if (demoEnabled && StringUtils.hasText(webRequest.getHeader("X-User-Id"))) {
            return readDemoUser(webRequest);
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return identityService.resolve(jwtAuthentication.getToken());
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "A valid Keycloak access token is required");
    }

    private static UserContext readDemoUser(NativeWebRequest request) {
        String userId = requiredHeader(request, "X-User-Id");
        String name = decodeName(requiredHeader(request, "X-User-Name-Base64"));
        String companyId = requiredHeader(request, "X-Company-Id");
        String role = requiredHeader(request, "X-User-Role");
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
