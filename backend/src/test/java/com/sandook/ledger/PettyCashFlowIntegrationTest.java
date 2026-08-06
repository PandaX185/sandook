package com.sandook.ledger;

import com.sandook.ledger.audit.AuditLogRepository;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.pettycash.PettyCashTransactionRepository;
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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
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
class PettyCashFlowIntegrationTest {

    private static final String PASSWORD = "***";

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
    PettyCashTransactionRepository pettyCashRepository;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long shopBookId;

    @BeforeEach
    void seed() {
        auditLogRepository.deleteAll();
        cashDayRepository.deleteAll();
        pettyCashRepository.deleteAll();
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
    void putAutoCreatesLinkedCashDayWithdraw() throws Exception {
        String token = login("editor");

        mockMvc.perform(post(transactionsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx("2026-08-01", "Top-up", "PUT", 10000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("PUT"))
                .andExpect(jsonPath("$.amountMinor").value(10000))
                .andExpect(jsonPath("$.balanceMinor").value(10000))
                .andExpect(jsonPath("$.linkedCashDayId").isNumber())
                .andExpect(jsonPath("$.linkedCashDayWithdrawMinor").value(10000));

        // Cash day row was auto-created with the withdraw
        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].withdrawMinor").value(10000))
                .andExpect(jsonPath("$[0].salesMinor").value(0));
    }

    @Test
    void putOnExistingCashDayAddsToWithdraw() throws Exception {
        String token = login("editor");
        createCashDay(token, "2026-08-01", 50000, 2000);

        mockMvc.perform(post(transactionsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx("2026-08-01", "Top-up", "PUT", 3000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCashDayWithdrawMinor").value(5000));

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].withdrawMinor").value(5000))
                .andExpect(jsonPath("$[0].balanceMinor").value(45000));
    }

    @Test
    void takeDoesNotTouchCashDays() throws Exception {
        String token = login("editor");
        createTx(token, "2026-08-01", "Office supplies", "TAKE", 3000);

        mockMvc.perform(get(transactionsUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].linkedCashDayId", nullValue()));

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void balanceIsPutMinusTake() throws Exception {
        String token = login("editor");
        createTx(token, "2026-08-01", "Top-up", "PUT", 10000);
        createTx(token, "2026-08-02", "Spend", "TAKE", 3000);
        createTx(token, "2026-08-03", "Top-up", "PUT", 5000);

        mockMvc.perform(get(balanceUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(12000));

        mockMvc.perform(get(balanceUrl() + "?asOf=2026-08-02")
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.balanceMinor").value(7000));

        mockMvc.perform(get(transactionsUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].balanceMinor").value(10000))
                .andExpect(jsonPath("$[1].balanceMinor").value(7000))
                .andExpect(jsonPath("$[2].balanceMinor").value(12000));
    }

    @Test
    void deletePutPrunesEmptyCashDay() throws Exception {
        String token = login("editor");
        long id = createTx(token, "2026-08-01", "Top-up", "PUT", 5000);

        mockMvc.perform(delete(transactionsUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", empty()));

        mockMvc.perform(get(balanceUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.balanceMinor").value(0));
    }

    @Test
    void updatePutMovesWithdraw() throws Exception {
        String token = login("editor");
        long id = createTx(token, "2026-08-01", "Top-up", "PUT", 10000);

        // Increase amount: day withdraw follows
        mockMvc.perform(put(transactionsUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx("2026-08-01", "Top-up", "PUT", 20000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCashDayWithdrawMinor").value(20000));

        // Change date: withdraw moves to the new day
        mockMvc.perform(put(transactionsUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx("2026-08-05", "Top-up", "PUT", 20000)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedCashDayWithdrawMinor").value(20000));

        mockMvc.perform(get(cashDaysUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].date").value("2026-08-05"))
                .andExpect(jsonPath("$[0].withdrawMinor").value(20000));
    }

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        String viewerToken = login("viewer");

        mockMvc.perform(post(transactionsUrl())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx("2026-08-01", "Top-up", "PUT", 10000)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(transactionsUrl())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));

        mockMvc.perform(get(balanceUrl())
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceMinor").value(0));
    }

    @Test
    void unknownBookReturns404() throws Exception {
        String token = login("editor");

        mockMvc.perform(get("/api/v1/books/999999/petty-cash/balance")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/books/999999/petty-cash/transactions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx("2026-08-01", "Top-up", "PUT", 10000)))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        String token = login("editor");

        mockMvc.perform(post(transactionsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-01\",\"description\":\"\",\"type\":\"PUT\",\"amountMinor\":-1}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(transactionsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-01\",\"description\":\"x\",\"type\":\"FLIP\",\"amountMinor\":100}"))
                .andExpect(status().isBadRequest());
    }

    // --- helpers ---

    private String transactionsUrl() {
        return "/api/v1/books/" + shopBookId + "/petty-cash/transactions";
    }

    private String balanceUrl() {
        return "/api/v1/books/" + shopBookId + "/petty-cash/balance";
    }

    private String cashDaysUrl() {
        return "/api/v1/books/" + shopBookId + "/cash-days";
    }

    private long createTx(String token, String date, String description, String type, long amount)
            throws Exception {
        MvcResult result = mockMvc.perform(post(transactionsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tx(date, description, type, amount)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createCashDay(String token, String date, long sales, long withdraw) throws Exception {
        mockMvc.perform(post(cashDaysUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"salesMinor\":" + sales
                                + ",\"extraMinor\":0,\"withdrawMinor\":" + withdraw + ",\"depositMinor\":0}"))
                .andExpect(status().isOk());
    }

    private String tx(String date, String description, String type, long amount) {
        return "{\"date\":\"" + date + "\",\"description\":\"" + description
                + "\",\"type\":\"" + type + "\",\"amountMinor\":" + amount + "}";
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
