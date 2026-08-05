package com.sandook.ledger;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "sandook.jwt.secret=test-secret-test-secret-test-secret-test-secret-0123456789",
        "sandook.admin.password=",
        "sandook.jwt.access-ttl-minutes=15"
})
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String PASSWORD = "pass123";

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
    RefreshTokenRepository refreshTokenRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void seedUsers() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("editor", Role.EDITOR));
        userRepository.save(user("viewer", Role.VIEWER));
    }

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void loginReturnsTokensAndGrantsAccess() throws Exception {
        String accessToken = login("editor").accessToken();

        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("editor"))
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"editor\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anonymousCannotAccessProtectedEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewerCannotListUsersButEditorCan() throws Exception {
        String viewerToken = login("viewer").accessToken();
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isForbidden());

        String editorToken = login("editor").accessToken();
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + editorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void refreshRotatesTokensAndInvalidatesOld() throws Exception {
        LoginResult first = login("editor");

        String body = "{\"refreshToken\":\"" + first.refreshToken() + "\"}";
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        JsonNode refreshed = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newRefreshToken = refreshed.get("refreshToken").asText();

        // Rotation: the new token differs from the old one
        if (newRefreshToken.equals(first.refreshToken())) {
            throw new AssertionError("Refresh token was not rotated");
        }

        // Reusing the old token must now fail
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithGarbageReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"not-a-real-token\"}"))
                .andExpect(status().isUnauthorized());
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
