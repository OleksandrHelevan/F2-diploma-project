package com.bricklayers.userservice.service.impl;

import com.bricklayers.userservice.annotation.AuditAdminAction;
import com.bricklayers.userservice.annotation.ForbidSelfTarget;
import com.bricklayers.userservice.annotation.RequireKeycloakLink;
import com.bricklayers.userservice.dto.CreateUserRequest;
import com.bricklayers.userservice.dto.SetPasswordRequest;
import com.bricklayers.userservice.dto.UpdateUserRequest;
import com.bricklayers.userservice.dto.UserDto;
import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.entity.User;
import com.bricklayers.userservice.exception.UserAlreadyExistsException;
import com.bricklayers.userservice.exception.UserNotFoundException;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakCredential;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakRole;
import com.bricklayers.userservice.integration.keycloak.KeycloakAdminClient.KeycloakUserRepresentation;
import com.bricklayers.userservice.mapper.UserMapper;
import com.bricklayers.userservice.repository.UserRepository;
import com.bricklayers.userservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final KeycloakAdminClient keycloakAdminClient;

    @Override
    @AuditAdminAction("USER_CREATE")
    public UserDto create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new UserAlreadyExistsException(
                    "user.already.exists.username", request.username());
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(
                    "user.already.exists.email", request.email());
        }

        String accessToken = keycloakAdminClient.getAccessToken();
        String keycloakId;
        try {
            keycloakId = keycloakAdminClient.createUser(
                    request.username(),
                    request.email(),
                    null,
                    request.firstName(),
                    request.lastName(),
                    request.companyId(),
                    accessToken);
        } catch (HttpClientErrorException.Conflict exception) {
            throw new UserAlreadyExistsException("user.already.exists.account");
        }

        try {
            keycloakAdminClient.assignRealmRole(
                    keycloakId,
                    keycloakAdminClient.getRealmRole(request.role().name(), accessToken),
                    accessToken);
            User user = userMapper.toEntity(request);
            user.setKeycloakId(keycloakId);
            user.setActive(true);
            User saved = userRepository.saveAndFlush(user);
            return userMapper.toDto(saved);
        } catch (RuntimeException exception) {
            deleteKeycloakUserAfterFailure(keycloakId, accessToken, exception);
            throw exception;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getById(UUID id) {
        return userMapper.toDto(findUserOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDto getByKeycloakId(String keycloakId) {
        User user = userRepository.findByKeycloakId(keycloakId)
                .orElseThrow(() -> new UserNotFoundException("user.not.found", keycloakId));
        return userMapper.toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserDto> getUsers(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        return userRepository
                .findAll(pageable)
                .map(userMapper::toDto);
    }

    @Override
    @AuditAdminAction("USER_UPDATE")
    @RequireKeycloakLink
    public UserDto update(UUID id, UpdateUserRequest request) {
        User user = findUserOrThrow(id);

        boolean emailChanged = request.email() != null && !request.email().equals(user.getEmail());
        if (emailChanged && userRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException(
                    "user.already.exists.email", request.email());
        }

        Role previousRole = user.getRole();
        userMapper.updateEntityFromRequest(request, user);
        User saved = userRepository.saveAndFlush(user);
        String accessToken = keycloakAdminClient.getAccessToken();
        keycloakAdminClient.updateUser(
                user.getKeycloakId(),
                keycloakRepresentation(
                        user.getUsername(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.isActive(),
                        user.getCompanyId()),
                accessToken);
        if (previousRole != user.getRole()) {
            List<KeycloakRole> assignedRoles = keycloakAdminClient.getRealmRoles(user.getKeycloakId(), accessToken);
            assignedRoles.stream()
                    .filter(role -> previousRole.name().equals(role.name()))
                    .forEach(role -> keycloakAdminClient.removeRealmRole(user.getKeycloakId(), role, accessToken));
            keycloakAdminClient.assignRealmRole(
                    user.getKeycloakId(),
                    keycloakAdminClient.getRealmRole(user.getRole().name(), accessToken),
                    accessToken);
        }
        return userMapper.toDto(saved);
    }

    @Override
    @AuditAdminAction("USER_DELETE")
    @ForbidSelfTarget(message = "Administrators cannot delete their own account")
    public void delete(UUID id) {
        User user = findUserOrThrow(id);
        if (user.getKeycloakId() != null && !user.getKeycloakId().isBlank()) {
            keycloakAdminClient.deleteUser(user.getKeycloakId(), keycloakAdminClient.getAccessToken());
        }
        userRepository.delete(user);
    }

    /**
     * Lab 4 (AOP), deliberate self-invocation: {@code this.delete(id)} bypasses the Spring proxy,
     * so neither {@link ForbidSelfTarget} nor {@link AuditAdminAction} on {@link #delete(UUID)} runs
     * here. An administrator can delete their own account through this path, and no audit record
     * is written, without any error. Calling {@code delete(id)} on the injected bean is guarded.
     */
    @Override
    public void deleteAll(List<UUID> ids) {
        ids.forEach(this::delete);
    }

    @Override
    @AuditAdminAction("USER_PASSWORD_RESET")
    @RequireKeycloakLink
    public void setPassword(UUID userId, SetPasswordRequest request){
        var user = findUserOrThrow(userId);
        String accessToken = keycloakAdminClient.getAccessToken();
        keycloakAdminClient.updatePassword(user.getKeycloakId(), request.password(), accessToken);

    }

    private User findUserOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("user.not.found", id));
    }

    private static KeycloakUserRepresentation keycloakRepresentation(
            String username,
            String email,
            String firstName,
            String lastName,
            boolean enabled,
            UUID companyId
    ) {
        Map<String, List<String>> attributes = companyId == null
                ? Map.of()
                : Map.of("companyId", List.of(companyId.toString()));
        return new KeycloakUserRepresentation(
                username, email, firstName, lastName, enabled, null, null, attributes);
    }

    private void deleteKeycloakUserAfterFailure(String keycloakId, String accessToken, RuntimeException failure) {
        try {
            keycloakAdminClient.deleteUser(keycloakId, accessToken);
        } catch (RuntimeException cleanupException) {
            failure.addSuppressed(cleanupException);
        }
    }
}