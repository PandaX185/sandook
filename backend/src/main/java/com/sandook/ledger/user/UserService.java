package com.sandook.ledger.user;

import com.sandook.ledger.audit.AuditService;
import com.sandook.ledger.common.ConflictException;
import com.sandook.ledger.common.NotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserResponse me(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, String actorUsername) {
        if (userRepository.existsByUsername(request.username())) {
            throw new ConflictException("Username already exists: " + request.username());
        }

        User user = new User();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setActive(true);
        userRepository.save(user);

        UserResponse response = UserResponse.from(user);
        auditService.record("CREATE", "user", user.getId(), null, response);
        return response;
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, String actorUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found: " + id));

        User actor = userRepository.findByUsername(actorUsername)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + actorUsername));

        // Safety rule A: you cannot demote or deactivate your own account.
        boolean demotingSelf = actor.getId().equals(user.getId())
                && (Boolean.FALSE.equals(request.active())
                        || (request.role() != null && request.role() != user.getRole()));
        if (demotingSelf) {
            throw new ConflictException("You cannot demote or deactivate your own account");
        }

        // Safety rule B: never remove the last active editor.
        boolean removingEditor = user.getRole() == Role.EDITOR
                && user.isActive()
                && (Boolean.FALSE.equals(request.active())
                        || request.role() == Role.VIEWER);
        if (removingEditor && userRepository.countByRoleAndActive(Role.EDITOR, true) <= 1) {
            throw new ConflictException("Cannot remove the last active editor");
        }

        UserResponse oldValue = UserResponse.from(user);

        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.active() != null) {
            user.setActive(request.active());
        }
        if (request.password() != null && !request.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        UserResponse response = UserResponse.from(user);
        auditService.record("UPDATE", "user", user.getId(), oldValue, response);
        return response;
    }
}
