package com.sandook.ledger.user;

import com.sandook.ledger.audit.AuditService;
import com.sandook.ledger.common.ConflictException;
import com.sandook.ledger.common.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuditService auditService;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditService = mock(AuditService.class);
        userService = new UserService(userRepository, passwordEncoder, auditService);
    }

    private User user(Long id, String username, Role role, boolean active) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setUsername(username);
        user.setPasswordHash("$argon2id$existing");
        user.setRole(role);
        user.setActive(active);
        return user;
    }

    // --- create ---

    @Test
    void createEncodesPasswordSavesAndAudits() {
        when(passwordEncoder.encode("secret123")).thenReturn("$argon2id$encoded");
        when(userRepository.existsByUsername("newbie")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        UserResponse response = userService.create(
                new CreateUserRequest("newbie", "secret123", Role.VIEWER),
                "admin");

        assertEquals("newbie", response.username());
        assertEquals(Role.VIEWER, response.role());
        assertTrue(response.active());

        verify(passwordEncoder).encode("secret123");
        verify(userRepository).save(any(User.class));
        verify(auditService).record(eq("CREATE"), eq("user"), eq(42L), isNull(), any(UserResponse.class));
    }

    @Test
    void createDuplicateUsernameThrowsConflict() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> userService.create(new CreateUserRequest("taken", "secret123", Role.VIEWER), "admin"));

        verify(userRepository, never()).save(any(User.class));
        verify(auditService, never()).record(any(), any(), any(), any(), any());
    }

    // --- update ---

    @Test
    void updateRoleAndActiveAuditsOldAndNewValues() {
        User target = user(2L, "viewer1", Role.VIEWER, true);
        User actor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));

        UserResponse response = userService.update(
                2L,
                new UpdateUserRequest(Role.EDITOR, false, null),
                "admin");

        assertEquals(Role.EDITOR, response.role());
        assertEquals(false, response.active());

        verify(auditService).record(eq("UPDATE"), eq("user"), eq(2L),
                eq(new UserResponse(2L, "viewer1", Role.VIEWER, true)),
                eq(new UserResponse(2L, "viewer1", Role.EDITOR, false)));
    }

    @Test
    void updatePasswordReEncodesHash() {
        User target = user(2L, "viewer1", Role.VIEWER, true);
        User actor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(passwordEncoder.encode("newpass123")).thenReturn("$argon2id$new");

        userService.update(2L, new UpdateUserRequest(null, null, "newpass123"), "admin");

        assertEquals("$argon2id$new", target.getPasswordHash());
    }

    @Test
    void updateMissingUserThrowsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class,
                () -> userService.update(99L, new UpdateUserRequest(null, false, null), "admin"));
    }

    // --- safety rules ---

    @Test
    void selfDemotionThrowsConflict() {
        User editor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(editor));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(editor));

        assertThrows(ConflictException.class,
                () -> userService.update(1L, new UpdateUserRequest(Role.VIEWER, null, null), "admin"));
    }

    @Test
    void selfDeactivationThrowsConflict() {
        User editor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(editor));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(editor));

        assertThrows(ConflictException.class,
                () -> userService.update(1L, new UpdateUserRequest(null, false, null), "admin"));
    }

    @Test
    void deactivatingLastActiveEditorThrowsConflict() {
        User target = user(2L, "editor2", Role.EDITOR, true);
        User actor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(userRepository.countByRoleAndActive(Role.EDITOR, true)).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> userService.update(2L, new UpdateUserRequest(null, false, null), "admin"));
    }

    @Test
    void demotingLastActiveEditorToViewerThrowsConflict() {
        User target = user(2L, "editor2", Role.EDITOR, true);
        User actor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(userRepository.countByRoleAndActive(Role.EDITOR, true)).thenReturn(1L);

        assertThrows(ConflictException.class,
                () -> userService.update(2L, new UpdateUserRequest(Role.VIEWER, null, null), "admin"));
    }

    @Test
    void deactivatingEditorWithAnotherActiveEditorSucceeds() {
        User target = user(2L, "editor2", Role.EDITOR, true);
        User actor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(target));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(actor));
        when(userRepository.countByRoleAndActive(Role.EDITOR, true)).thenReturn(2L);

        UserResponse response = userService.update(
                2L, new UpdateUserRequest(null, false, null), "admin");

        assertEquals(false, response.active());
    }

    @Test
    void selfUpdateWithNoDemotionIsAllowed() {
        User editor = user(1L, "admin", Role.EDITOR, true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(editor));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(editor));

        UserResponse response = userService.update(
                1L, new UpdateUserRequest(Role.EDITOR, true, null), "admin");

        assertEquals(Role.EDITOR, response.role());
        assertTrue(response.active());
    }
}
