package com.sandook.ledger;

import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDay;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.parking.ParkingCashMove;
import com.sandook.ledger.parking.ParkingCashMoveRepository;
import com.sandook.ledger.parking.ParkingCashMoveType;
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
class TransferFlowIntegrationTest {

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
    BookRepository bookRepository;

    @Autowired
    CashDayRepository cashDayRepository;

    @Autowired
    ParkingCashMoveRepository parkingMoveRepository;

    @Autowired
    TransferRepository transferRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Long parkingBookId;
    private Long shopBookId;

    @BeforeEach
    void seed() {
        cashDayRepository.deleteAll();
        parkingMoveRepository.deleteAll();
        transferRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("editor", Role.EDITOR));
        userRepository.save(user("viewer", Role.VIEWER));
        parkingBookId = bookByName("Parking").getId();
        shopBookId = bookByName("Shop").getId();
    }

    @Test
    void plainTransferCreatesNoLedgerLinks() throws Exception {
        String token = login("editor");

        MvcResult created = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plainTransfer("2026-08-01", 100000, "rent")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedParkingMove").value(false))
                .andExpect(jsonPath("$.linkedMoveId").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // No parking move, no cash day.
        assert parkingMoveRepository.count() == 0 : "plain transfer must not create parking moves";
        assert cashDayRepository.count() == 0 : "plain transfer must not create cash days";

        mockMvc.perform(get("/api/v1/transfers/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ref").value("rent"))
                .andExpect(jsonPath("$.currencyCode").value("AED"));
    }

    @Test
    void linkedTransferAutomatesParkingMoveAndShopExtra() throws Exception {
        String token = login("editor");

        MvcResult created = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkedTransfer("2026-06-01", 12500, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedParkingMove").value(true))
                .andExpect(jsonPath("$.linkedMoveId").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.linkedCashDayId").value(org.hamcrest.Matchers.notNullValue()))
                .andExpect(jsonPath("$.linkedCashDayExtraMinor").value(12500))
                .andReturn();

        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        long moveId = json.get("linkedMoveId").asLong();
        long dayId = json.get("linkedCashDayId").asLong();
        long transferId = json.get("id").asLong();

        // Parking move exists on the Parking book, type TRANSFER_TO_SHOP, linked back.
        ParkingCashMove move = parkingMoveRepository.findById(moveId).orElseThrow();
        assert move.getBookId().equals(parkingBookId) : "move must be on parking book";
        assert move.getType() == ParkingCashMoveType.TRANSFER_TO_SHOP : "move type";
        assert move.getTransferId().equals(transferId) : "move must reference the transfer";

        // Shop cash day auto-created with extra 12500.
        CashDay day = cashDayRepository.findById(dayId).orElseThrow();
        assert day.getBookId().equals(shopBookId) : "day must be on shop book";
        assert day.getExtraMinor() == 12500 : "extra bump";

        // Parking statement shows the transfer as an outflow.
        mockMvc.perform(get("/api/v1/books/" + parkingBookId + "/parking/cash-moves/statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].transfersToShopMinor").value(12500));

        // List filtered by book shows it from both sides.
        mockMvc.perform(get("/api/v1/transfers?bookId=" + shopBookId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void updatingLinkedTransferReversesAndReapplies() throws Exception {
        String token = login("editor");

        MvcResult created = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkedTransfer("2026-06-01", 12500, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        long transferId = json.get("id").asLong();

        // Same amount, later date → the old day row (2026-06-01) must be pruned,
        // a new day row created for 2026-06-02.
        mockMvc.perform(put("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-06-02\",\"amountMinor\":12500,\"ref\":\"parking cash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.linkedParkingMove").value(true))
                .andExpect(jsonPath("$.date").value("2026-06-02"))
                .andExpect(jsonPath("$.linkedCashDayExtraMinor").value(12500));

        assert cashDayRepository.findByBookIdAndDate(shopBookId, java.time.LocalDate.of(2026, 6, 1))
                .isEmpty() : "old day row must be pruned";
        CashDay newDay = cashDayRepository
                .findByBookIdAndDate(shopBookId, java.time.LocalDate.of(2026, 6, 2)).orElseThrow();
        assert newDay.getExtraMinor() == 12500 : "new day row";

        // One linked move only (old one replaced).
        assert parkingMoveRepository.findAll().size() == 1 : "old linked move must be replaced";
    }

    @Test
    void deletingLinkedTransferReversesEverything() throws Exception {
        String token = login("editor");

        MvcResult created = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkedTransfer("2026-06-01", 12500, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        long transferId = json.get("id").asLong();

        mockMvc.perform(delete("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        assert parkingMoveRepository.count() == 0 : "linked move must be deleted";
        assert cashDayRepository.count() == 0 : "day row must be pruned";
    }

    @Test
    void unlinkingTransferConflictsWhenExtraWasSpent() throws Exception {
        String token = login("editor");

        MvcResult created = mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(linkedTransfer("2026-06-01", 12500, null)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        long transferId = json.get("id").asLong();

        // Simulate the shop having spent part of the extra: drop it to 5000.
        CashDay day = cashDayRepository.findById(json.get("linkedCashDayId").asLong()).orElseThrow();
        day.setExtraMinor(5000);
        cashDayRepository.save(day);

        mockMvc.perform(delete("/api/v1/transfers/" + transferId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict());
    }

    @Test
    void sameBookTransferIsRejected() throws Exception {
        String token = login("editor");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBookId\":" + parkingBookId + ",\"toBookId\":" + parkingBookId
                                + ",\"date\":\"2026-08-01\",\"amountMinor\":1000}"))
                .andExpect(status().isConflict());
    }

    @Test
    void linkedTransferFromNonParkingBookIsRejected() throws Exception {
        String token = login("editor");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBookId\":" + shopBookId + ",\"toBookId\":" + parkingBookId
                                + ",\"date\":\"2026-08-01\",\"amountMinor\":12500,\"linkParkingMove\":true}"))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownBookIsNotFound() throws Exception {
        String token = login("editor");
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromBookId\":999999,\"toBookId\":" + shopBookId
                                + ",\"date\":\"2026-08-01\",\"amountMinor\":1000}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void viewerCannotCreateTransfers() throws Exception {
        String token = login("viewer");

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(plainTransfer("2026-08-01", 1000, null)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/transfers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    // --- helpers ---

    private String plainTransfer(String date, long amount, String ref) {
        return "{\"fromBookId\":" + parkingBookId + ",\"toBookId\":" + shopBookId
                + ",\"date\":\"" + date + "\",\"amountMinor\":" + amount
                + (ref == null ? "" : ",\"ref\":\"" + ref + "\"") + "}";
    }

    private String linkedTransfer(String date, long amount, String ref) {
        return "{\"fromBookId\":" + parkingBookId + ",\"toBookId\":" + shopBookId
                + ",\"date\":\"" + date + "\",\"amountMinor\":" + amount
                + (ref == null ? "" : ",\"ref\":\"" + ref + "\"")
                + ",\"linkParkingMove\":true}";
    }

    private Book bookByName(String name) {
        return bookRepository.findAll().stream()
                .filter(b -> b.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Book not seeded: " + name));
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
