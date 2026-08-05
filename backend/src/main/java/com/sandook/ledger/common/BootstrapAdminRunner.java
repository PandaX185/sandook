package com.sandook.ledger.common;

import com.sandook.ledger.user.Role;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first EDITOR user on startup when the users table is empty.
 * Credentials come from ADMIN_USERNAME / ADMIN_PASSWORD env vars — never hardcoded.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${sandook.admin.username:admin}")
    private String adminUsername;

    @Value("${sandook.admin.password:}")
    private String adminPassword;

    public BootstrapAdminRunner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            log.warn("No users in DB and ADMIN_PASSWORD is not set — skipping bootstrap admin. "
                    + "Set ADMIN_USERNAME/ADMIN_PASSWORD to create the first editor.");
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setRole(Role.EDITOR);
        admin.setActive(true);
        userRepository.save(admin);
        log.info("Bootstrap admin '{}' created with role EDITOR", adminUsername);
    }
}
