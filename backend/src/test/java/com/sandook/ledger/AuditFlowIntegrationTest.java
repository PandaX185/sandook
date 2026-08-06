package com.sandook.ledger;

import com.sandook.ledger.audit.AuditLogRepository;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.parking.ParkingBillRepository;
import com.sandook.ledger.parking.ParkingBookingRepository;
import com.sandook.ledger.parking.ParkingCashMoveRepository;
import com.sandook.ledger.parking.ParkingSalaryPaymentRepository;
import com.sandook.ledger.transfer.TransferRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every write (create/update/delete) on the six write services must append an
 * audit_log row with actor, old/new JSON snapshots, in the same transaction.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "sandook.jwt.secret=test-secret-test-secret-test-secret-test-secret-0123456789",
        "sandook.admin.password=",
        "sandook.jwt.access-ttl-minutes=15"
})
@AutoConfigureMockMvc
class AuditFlowIntegrationTest {

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
    ParkingCashMoveRepository parkingMoveRepository;

    @Autowired
    ParkingBillRepository parkingBillRepository;

    @Autowired
    ParkingBookingRepository parkingBookingRepository;

    @Autowired
    ParkingSalaryPaymentRepository parkingSalaryPaymentRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    AuditLogRepository auditLogRepository;

    @Autowired
    BookRepository bookRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long parkingBookId;

    @BeforeEach
    void seed() {
        auditLogRepository.deleteAll();
        parkingSalaryPaymentRepository.deleteAll();
        parkingBookingRepository.deleteAll();
        parkingBillRepository.deleteAll();
        parkingMoveRepository.deleteAll();
        transferRepository.deleteAll();
        cashDayRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("editor", Role.EDITOR));
        userRepository.save(user("viewer", Role.VIEWER));
        parkingBookId = bookByName("Parking");
    }

    @Test
    void billCrudAppendsAuditEntries() throws Exception {
        String token = login("editor");

        // CREATE
        MvcResult created = mockMvc.perform(post("/api/v1/books/" + parkingBookId + "/parking/bills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"A1234\",\"amountMinor\":2500,\"paymentMethod\":\"CASH\",\"billedAt\":\"2026-08-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn();
        long billId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // UPDATE
        mockMvc.perform(put("/api/v1/books/" + parkingBookId + "/parking/bills/" + billId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"A1234\",\"amountMinor\":3000,\"paymentMethod\":\"CARD\",\"billedAt\":\"2026-08-01\"}"))
                .andExpect(status().isOk());

        // DELETE
        mockMvc.perform(delete("/api/v1/books/" + parkingBookId + "/parking/bills/" + billId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        // Audit trail: 3 entries, newest first, all by editor, parking_bill entity.
        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].action").value("DELETE"))
                .andExpect(jsonPath("$[0].username").value("editor"))
                .andExpect(jsonPath("$[0].entity").value("parking_bill"))
                .andExpect(jsonPath("$[0].entityId").value(billId))
                .andExpect(jsonPath("$[0].oldValue.plateNo").value("A1234"))
                .andExpect(jsonPath("$[0].newValue").value(nullValue()))
                .andExpect(jsonPath("$[1].action").value("UPDATE"))
                .andExpect(jsonPath("$[1].oldValue.amountMinor").value(2500))
                .andExpect(jsonPath("$[1].newValue.amountMinor").value(3000))
                .andExpect(jsonPath("$[1].newValue.paymentMethod").value("CARD"))
                .andExpect(jsonPath("$[2].action").value("CREATE"))
                .andExpect(jsonPath("$[2].oldValue").value(nullValue()))
                .andExpect(jsonPath("$[2].newValue.plateNo").value("A1234"))
                .andExpect(jsonPath("$[2].createdAt", notNullValue()));
    }

    @Test
    void filtersWorkByEntityAndAction() throws Exception {
        String token = login("editor");

        mockMvc.perform(post("/api/v1/books/" + parkingBookId + "/parking/bills")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"B5678\",\"amountMinor\":1000,\"paymentMethod\":\"CASH\",\"billedAt\":\"2026-08-02\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit?entity=parking_bill&action=CREATE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].entity").value("parking_bill"))
                .andExpect(jsonPath("$[0].action").value("CREATE"));

        // Unknown action → no rows.
        mockMvc.perform(get("/api/v1/audit?action=REFRESH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Unknown entity → no rows.
        mockMvc.perform(get("/api/v1/audit?entity=spaceship")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void linkedTransferAndMoveAreAuditedTogether() throws Exception {
        String token = login("editor");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBookId\":" + parkingBookId
                                + ",\"toBookId\":" + bookByName("Shop")
                                + ",\"date\":\"2026-06-01\",\"amountMinor\":12500,\"linkParkingMove\":true}"))
                .andExpect(status().isOk());

        // The transfer row itself is audited…
        mockMvc.perform(get("/api/v1/audit?entity=transfer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].newValue.linkedParkingMove").value(true))
                .andExpect(jsonPath("$[0].newValue.amountMinor").value(12500));

        // …and so is the auto-created parking cash move.
        mockMvc.perform(get("/api/v1/audit?entity=parking_cash_move")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("CREATE"))
                .andExpect(jsonPath("$[0].newValue.type").value("TRANSFER_TO_SHOP"))
                .andExpect(jsonPath("$[0].newValue.amountMinor").value(12500));
    }

    @Test
    void viewerCanReadAuditButNotWrite() throws Exception {
        String viewerToken = login("viewer");

        mockMvc.perform(get("/api/v1/audit")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());

        // No POST endpoint exists on /api/v1/audit at all — a POST must 405.
        mockMvc.perform(post("/api/v1/audit")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }

    // --- helpers ---

    private Long bookByName(String name) {
        return bookRepository.findAll().stream()
                .filter(b -> b.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Book not seeded: " + name))
                .getId();
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
