package com.sandook.ledger;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.sandook.ledger.audit.AuditLogRepository;
import com.sandook.ledger.auth.LoginRequest;
import com.sandook.ledger.user.RefreshTokenRepository;
import com.sandook.ledger.user.Role;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "sandook.jwt.secret=test-secret-test-secret-test-secret-test-secret-0123456789",
        "sandook.admin.password=",
        "sandook.jwt.access-ttl-minutes=15"
})
@AutoConfigureMockMvc
class UsersFlowIntegrationTest {

    private static final String PASSWORD = "Password123";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    UserRepository userRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        auditLogRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("admin", Role.EDITOR));
        userRepository.save(user("editor2", Role.EDITOR));
        userRepository.save(user("viewer", Role.VIEWER));
    }

    @Test
    void editorCreatesUserAndNewUserCanLogin() throws Exception {
        String adminToken = login("admin").accessToken();

        MvcResult createResult = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cashier\",\"password\":\"Cashier123\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("cashier"))
                .andExpect(jsonPath("$.role").value("VIEWER"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        long newId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Appears in the list
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));

        // Can log in with the new credentials
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cashier\",\"password\":\"Cashier123\"}"))
                .andExpect(status().isOk());

        // Audit entry recorded
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("entity", "user")
                        .param("action", "CREATE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entity").value("user"))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].entityId").value(newId));
    }

    @Test
    void duplicateUsernameReturns409() throws Exception {
        String adminToken = login("admin").accessToken();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Cashier123\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shortPasswordReturns400() throws Exception {
        String adminToken = login("admin").accessToken();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cashier\",\"password\":\"short\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void viewerCanListUsersButCannotWrite() throws Exception {
        String viewerToken = login("viewer").accessToken();
        long viewerId = userRepository.findByUsername("viewer").orElseThrow().getId();

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"cashier\",\"password\":\"Cashier123\",\"role\":\"VIEWER\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/users/" + viewerId)
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void deactivatedUserCannotLogin() throws Exception {
        String adminToken = login("admin").accessToken();
        long viewerId = userRepository.findByUsername("viewer").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/users/" + viewerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetTakesEffect() throws Exception {
        String adminToken = login("admin").accessToken();
        long viewerId = userRepository.findByUsername("viewer").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/users/" + viewerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"NewPass456\"}"))
                .andExpect(status().isOk());

        // Old password rejected
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());

        // New password works
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"viewer\",\"password\":\"NewPass456\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void selfDeactivationAndSelfDemotionReturn409() throws Exception {
        String adminToken = login("admin").accessToken();
        long adminId = userRepository.findByUsername("admin").orElseThrow().getId();

        mockMvc.perform(put("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
    }

    /**
     * Rule B via the stale-JWT path: editor2 demotes admin (2 editors -> 1),
     * then admin — whose access token still carries the EDITOR role claim for
     * up to the access-token TTL — tries to demote editor2 (the last active
     * editor). The server must refuse.
     */
    @Test
    void lastActiveEditorCannotBeRemoved() throws Exception {
        String adminToken = login("admin").accessToken();
        String editor2Token = login("editor2").accessToken();
        long adminId = userRepository.findByUsername("admin").orElseThrow().getId();
        long editor2Id = userRepository.findByUsername("editor2").orElseThrow().getId();

        // editor2 demotes admin -> now editor2 is the only active editor
        mockMvc.perform(put("/api/v1/users/" + adminId)
                        .header("Authorization", "Bearer " + editor2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isOk());

        // admin's token is still EDITOR (JWT not yet expired); the server must refuse
        mockMvc.perform(put("/api/v1/users/" + editor2Id)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"VIEWER\"}"))
                .andExpect(status().isConflict());
    }

    private User user(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private LoginResult login(String username) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginRequest(username, PASSWORD));
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return new LoginResult(json.get("accessToken").asText(), json.get("refreshToken").asText());
    }

    private record LoginResult(String accessToken, String refreshToken) {
    }
}
