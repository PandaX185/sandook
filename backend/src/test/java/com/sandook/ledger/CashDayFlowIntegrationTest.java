package com.sandook.ledger;

import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDayRepository;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CashDayFlowIntegrationTest {

    private static final String PASSWORD = "test-password";

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
    CashDayRepository cashDayRepository;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long shopBookId;

    @BeforeEach
    void seed() {
        cashDayRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("editor", Role.EDITOR));
        userRepository.save(user("viewer", Role.VIEWER));
        Book shop = bookRepository.findAll().stream()
                .filter(b -> b.getName().equals("Shop"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Shop book not seeded by Flyway"));
        shopBookId = shop.getId();
    }

    @Test
    void editorCanCreateDayAndBalanceIsComputed() throws Exception {
        String token = login("editor");

        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-01", 100000, 0, 0, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-01"))
                .andExpect(jsonPath("$.salesMinor").value(100000))
                .andExpect(jsonPath("$.netCashMinor").value(100000))
                .andExpect(jsonPath("$.balanceMinor").value(100000))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    @Test
    void runningBalanceAccumulatesAcrossDays() throws Exception {
        String token = login("editor");
        createDay(token, "2026-08-01", 100000, 0, 0, 0);
        createDay(token, "2026-08-02", 50000, 0, 0, 0);

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].balanceMinor").value(100000))
                .andExpect(jsonPath("$[1].balanceMinor").value(150000));
    }

    @Test
    void depositMatchingCashOnHandHasNoWarning() throws Exception {
        String token = login("editor");
        createDay(token, "2026-08-01", 100000, 0, 0, 0);

        // Day 2: cash on hand before deposit = 100000 + 50000 = 150000 → deposit all of it
        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-02", 50000, 0, 0, 150000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(0))
                .andExpect(jsonPath("$.warnings", empty()));
    }

    @Test
    void mismatchedDepositSurfacesWarningNotError() throws Exception {
        String token = login("editor");
        createDay(token, "2026-08-01", 100000, 0, 0, 0);

        // Deposit 100000 but 150000 is on hand → warning, still 200
        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-02", 50000, 0, 0, 100000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(50000))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0]", containsString("does not match")));
    }

    @Test
    void duplicateDateReturns409() throws Exception {
        String token = login("editor");
        createDay(token, "2026-08-01", 100000, 0, 0, 0);

        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-01", 1, 0, 0, 0)))
                .andExpect(status().isConflict());
    }

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        String viewerToken = login("viewer");

        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-01", 100000, 0, 0, 0)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void unknownBookReturns404() throws Exception {
        String token = login("editor");

        mockMvc.perform(get("/api/v1/books/999999/cash-days")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/books/999999/cash-days")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-01", 100000, 0, 0, 0)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateRecalculatesBalanceAndDeleteRemoves() throws Exception {
        String token = login("editor");
        long id = createDay(token, "2026-08-01", 100000, 0, 0, 0);

        mockMvc.perform(put(cashDaysUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-01", 200000, 0, 0, 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(200000));

        mockMvc.perform(delete(cashDaysUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void invalidAmountsReturn400() throws Exception {
        String token = login("editor");

        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day("2026-08-01", -5, 0, 0, 0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"salesMinor\":100}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String cashDaysUrl() {
        return "/api/v1/books/" + shopBookId + "/cash-days";
    }

    private long createDay(String token, String date, long sales, long extra, long withdraw, long deposit)
            throws Exception {
        MvcResult result = mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(day(date, sales, extra, withdraw, deposit)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private String day(String date, long sales, long extra, long withdraw, long deposit) {
        return "{\"date\":\"" + date + "\","
                + "\"salesMinor\":" + sales + ","
                + "\"extraMinor\":" + extra + ","
                + "\"withdrawMinor\":" + withdraw + ","
                + "\"depositMinor\":" + deposit + "}";
    }

    private User user(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(true);
        return user;
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("accessToken").asText();
    }
}
