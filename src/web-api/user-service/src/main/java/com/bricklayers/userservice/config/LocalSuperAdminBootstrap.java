package com.bricklayers.userservice.config;

import com.bricklayers.userservice.entity.Role;
import com.bricklayers.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocalSuperAdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${local-auth.bootstrap-superadmin-username:}")
    private String username;

    @Value("${local-auth.bootstrap-superadmin-password:}")
    private String password;

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            return;
        }

        var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Configured local bootstrap superadmin username does not exist in the users database"));
        if (user.getRole() != Role.SUPERADMIN) {
            throw new IllegalStateException("Configured local bootstrap user does not have SUPERADMIN role");
        }
        if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()) {
            log.info("Local bootstrap superadmin already has a password; leaving it unchanged");
            return;
        }

        user.setPasswordHash(passwordEncoder.encode(password));
        userRepository.save(user);
        log.info("Initialized local password for configured SUPERADMIN user");
    }
}
