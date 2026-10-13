package com.bricklayers.chatservice.security;

import com.bricklayers.chatservice.client.UserDirectory;
import com.bricklayers.chatservice.client.UserInfo;
import com.bricklayers.chatservice.entity.ParticipantRole;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ChatIdentityService {

    private final UserDirectory userDirectory;

    public UserContext resolve(Jwt jwt) {
        try {
            UserInfo profile = userDirectory.getByKeycloakId(
                    jwt.getSubject(), "Bearer " + jwt.getTokenValue());
            if (!profile.active()
                    || !StringUtils.hasText(profile.id())
                    || !StringUtils.hasText(profile.companyId())) {
                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "User profile is incomplete for chat access");
            }

            ParticipantRole role = readRoleFromToken(jwt);

            String name = (StringUtils.hasText(profile.firstName()) ? profile.firstName() : "")
                    + (StringUtils.hasText(profile.lastName()) ? " " + profile.lastName() : "");
            if (!StringUtils.hasText(name)) {
                name = profile.username();
            }
            return new UserContext(profile.id(), name, profile.companyId(), role);
        } catch (FeignException.NotFound exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Keycloak user has no linked user profile", exception);
        }
    }

    private static ParticipantRole readRoleFromToken(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map<?, ?> realmAccessMap)
                || !(realmAccessMap.get("roles") instanceof Collection<?> roles)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Keycloak token has no application role");
        }

        var applicationRoles = roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(ChatIdentityService::isApplicationRole)
                .distinct()
                .toList();
        if (applicationRoles.size() != 1) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Keycloak token must have one chat role");
        }
        try {
            return ParticipantRole.valueOf(applicationRoles.getFirst());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Keycloak role is not allowed to access chat", exception);
        }
    }

    private static boolean isApplicationRole(String role) {
        return Set.of("SUPERADMIN", "CUSTOMER", "FOREMAN", "BUILDER", "SUPPLIER").contains(role);
    }
}
