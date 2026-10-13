package com.bricklayers.userservice.integration.keycloak;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class KeycloakAdminClient {

    private static final ParameterizedTypeReference<List<KeycloakUser>> USERS_TYPE = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<KeycloakRole>> ROLES_TYPE = new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String adminBaseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakAdminClient(
            @Value("${keycloak.admin.base-url}") String adminBaseUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret
    ) {
        this.restClient = RestClient.builder().build();
        this.adminBaseUrl = adminBaseUrl.replaceAll("/+$", "");
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);

        TokenResponse token = restClient.post()
                .uri(endpoint("realms", realm, "protocol", "openid-connect", "token"))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TokenResponse.class);
        if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
            throw new IllegalStateException("Keycloak returned an empty service-account access token");
        }
        return token.accessToken();
    }

    public List<KeycloakUser> getUsers(int first, int max, String accessToken) {
        List<KeycloakUser> users = restClient.get()
                .uri(UriComponentsBuilder.fromUriString(adminBaseUrl)
                        .pathSegment("admin", "realms", realm, "users")
                        .queryParam("first", first)
                        .queryParam("max", max)
                        .build()
                        .encode()
                        .toUri())
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(USERS_TYPE);
        if (users == null) {
            throw new IllegalStateException("Keycloak returned an empty users response");
        }
        return users;
    }

    public List<KeycloakRole> getRealmRoles(String userId, String accessToken) {
        List<KeycloakRole> roles = restClient.get()
                .uri(endpoint("admin", "realms", realm, "users", userId, "role-mappings", "realm"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(ROLES_TYPE);
        return roles == null ? List.of() : roles;
    }

    public String createUser(String username,
                             String email,
                             String password,
                             String firstName,
                             String lastName,
                             String accessToken) {
        return createUser(username, email, password, firstName, lastName, null, accessToken);
    }

    public String createUser(String username,
                             String email,
                             String password,
                             String firstName,
                             String lastName,
                             UUID companyId,
                             String accessToken) {
        List<KeycloakCredential> credentials = password == null || password.isBlank()
                ? List.of()
                : List.of(new KeycloakCredential("password", password, false));
        Map<String, List<String>> attributes = companyId == null
                ? Map.of()
                : Map.of("companyId", List.of(companyId.toString()));
        KeycloakUserRepresentation user = new KeycloakUserRepresentation(
                username,
                email,
                firstName,
                lastName,
                true,
                false,
                credentials,
                attributes);
        URI location = restClient.post()
                .uri(endpoint("admin", "realms", realm, "users"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity()
                .getHeaders()
                .getLocation();
        if (location == null || location.getPath() == null) {
            throw new IllegalStateException("Keycloak did not return the created user's location");
        }
        String path = location.getPath();
        String userId = path.substring(path.lastIndexOf('/') + 1);
        if (userId.isBlank()) {
            throw new IllegalStateException("Keycloak returned an invalid created user's location");
        }
        return userId;
    }

    public void updateUser(String userId, KeycloakUserRepresentation user, String accessToken) {
        restClient.put()
                .uri(endpoint("admin", "realms", realm, "users", userId))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(user)
                .retrieve()
                .toBodilessEntity();
    }

    public KeycloakRealmRole getRealmRole(String roleName, String accessToken) {
        KeycloakRealmRole role = restClient.get()
                .uri(endpoint("admin", "realms", realm, "roles", roleName))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(KeycloakRealmRole.class);
        if (role == null || role.id() == null) {
            throw new IllegalStateException("Keycloak did not return realm role " + roleName);
        }
        return role;
    }

    public void assignRealmRole(String userId, KeycloakRealmRole role, String accessToken) {
        restClient.post()
                .uri(endpoint("admin", "realms", realm, "users", userId, "role-mappings", "realm"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    public void removeRealmRole(String userId, KeycloakRole role, String accessToken) {
        restClient.method(org.springframework.http.HttpMethod.DELETE)
                .uri(endpoint("admin", "realms", realm, "users", userId, "role-mappings", "realm"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role))
                .retrieve()
                .toBodilessEntity();
    }

    public void deleteUser(String userId, String accessToken) {
        restClient.delete()
                .uri(endpoint("admin", "realms", realm, "users", userId))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    public void updatePassword(String userId, String password, String accessToken) {
        restClient.put()
                .uri(endpoint("admin", "realms", realm, "users", userId, "reset-password"))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .body(new KeycloakCredential("password", password, false))
                .retrieve()
                .toBodilessEntity();
    }

    private java.net.URI endpoint(String... pathSegments) {
        return UriComponentsBuilder.fromUriString(adminBaseUrl)
                .pathSegment(pathSegments)
                .build()
                .encode()
                .toUri();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResponse(@JsonProperty("access_token") String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakUser(
            String id,
            String username,
            String email,
            String firstName,
            String lastName,
            Boolean enabled,
            java.util.Map<String, List<String>> attributes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakRole(String id, String name) {}

    public record KeycloakUserRepresentation(
            String username,
            String email,
            String firstName,
            String lastName,
            Boolean enabled,
            Boolean emailVerified,
            List<KeycloakCredential> credentials,
            Map<String, List<String>> attributes
    ) {}

    public record KeycloakCredential(String type, String value, boolean temporary) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KeycloakRealmRole(String id, String name) {}
}
