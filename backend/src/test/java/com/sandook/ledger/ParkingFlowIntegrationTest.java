package com.sandook.ledger;

import com.sandook.ledger.audit.AuditLogRepository;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.parking.ParkingBillRepository;
import com.sandook.ledger.parking.ParkingBookingRepository;
import com.sandook.ledger.parking.ParkingCashMoveRepository;
import com.sandook.ledger.parking.ParkingSalaryPaymentRepository;
import com.sandook.ledger.user.RefreshTokenRepository;
import com.sandook.ledger.user.Role;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import java.time.LocalDate;
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
class ParkingFlowIntegrationTest {

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
    ParkingBillRepository billRepository;

    @Autowired
    ParkingCashMoveRepository moveRepository;

    @Autowired
    ParkingSalaryPaymentRepository salaryPaymentRepository;

    @Autowired
    ParkingBookingRepository bookingRepository;

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
        cashDayRepository.deleteAll();
        billRepository.deleteAll();
        moveRepository.deleteAll();
        salaryPaymentRepository.deleteAll();
        bookingRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
        userRepository.save(user("editor", Role.EDITOR));
        userRepository.save(user("viewer", Role.VIEWER));
        parkingBookId = bookRepository.findAll().stream()
                .filter(b -> b.getName().equals("Parking"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Parking book not seeded by Flyway"))
                .getId();
    }

    @Test
    void billCrudAndSummary() throws Exception {
        String token = login("editor");

        MvcResult created = mockMvc.perform(post(billsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bill("A1234", 5000, "CASH", "2026-08-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNo").value("A1234"))
                .andExpect(jsonPath("$.amountMinor").value(5000))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andReturn();
        long id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post(billsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bill("B5678", 7000, "CARD", "2026-08-02")))
                .andExpect(status().isOk());

        mockMvc.perform(post(billsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bill("C9012", 3000, "CASH", "2026-08-03")))
                .andExpect(status().isOk());

        // Filters
        mockMvc.perform(get(billsUrl() + "?from=2026-08-02")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        mockMvc.perform(get(billsUrl() + "?plate=B567")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plateNo").value("B5678"));

        // Summary: cash 8000, card 7000, total 15000
        mockMvc.perform(get(billsUrl() + "/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashMinor").value(8000))
                .andExpect(jsonPath("$.cardMinor").value(7000))
                .andExpect(jsonPath("$.totalMinor").value(15000))
                .andExpect(jsonPath("$.count").value(3));

        // Update + delete
        mockMvc.perform(put(billsUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bill("A1234", 5500, "CASH", "2026-08-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amountMinor").value(5500));

        mockMvc.perform(delete(billsUrl() + "/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(billsUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void cashMoveBalanceMath() throws Exception {
        String token = login("editor");

        // Opening 12814, then outflows — mirrors the real June sheet.
        createMove(token, "2026-06-01", "OPENING", 12814);
        createMove(token, "2026-06-02", "EXPENSE", 192, "cleaning supplies", null);

        mockMvc.perform(get(movesUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].balanceMinor").value(12814))
                .andExpect(jsonPath("$[1].balanceMinor").value(12622));
    }

    @Test
    void transferToShopMoveIsReadOnly() throws Exception {
        String token = login("editor");

        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(move("2026-06-01", "TRANSFER_TO_SHOP", 12500, null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    void salaryMoveRequiresMatchingSplit() throws Exception {
        String token = login("editor");

        // Iqpal 833 + Habib 2500 + Raseem 2000 = 5333 (the real June salary)
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-06-28","type":"SALARY","amountMinor":5333,"description":"June salaries",
                                 "salaryPayments":[
                                   {"person":"Iqpal","amountMinor":833},
                                   {"person":"Habib","amountMinor":2500},
                                   {"person":"Raseem","amountMinor":2000}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryPayments", hasSize(3)))
                .andExpect(jsonPath("$.salaryPayments[0].person").value("Iqpal"))
                .andExpect(jsonPath("$.salaryPayments[2].amountMinor").value(2000));

        // Sum mismatch → 409
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-06-28","type":"SALARY","amountMinor":5333,"description":"June salaries",
                                 "salaryPayments":[{"person":"Iqpal","amountMinor":833}]}
                                """))
                .andExpect(status().isConflict());

        // No payments at all → 409
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(move("2026-06-28", "SALARY", 5333, "June salaries", null)))
                .andExpect(status().isConflict());
    }

    @Test
    void statementIncludesCardAndBookings() throws Exception {
        String token = login("editor");
        String futureDue = LocalDate.now().plusMonths(3).toString();

        // Bills: cash 1000 + 2000, card 3000 on 2026-06-01; card is now part of the balance.
        createBill(token, "A1", 1000, "CASH", "2026-06-01");
        createBill(token, "A2", 2000, "CASH", "2026-06-01");
        createBill(token, "A3", 3000, "CARD", "2026-06-01");

        // Booking payment (card, 50000) lands on 2026-06-01 → bookings column.
        MvcResult created = mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"P777\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + futureDue + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        long bookingId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post(bookingsUrl() + "/" + bookingId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":50000,\"paymentMethod\":\"CARD\",\"paidAt\":\"2026-06-01\"}"))
                .andExpect(status().isOk());

        // Moves: opening 12814; salary 5333 + expense 192 (with notes) on 28/6.
        createMove(token, "2026-06-01", "OPENING", 12814);
        createMove(token, "2026-06-28", "SALARY", 5333, "June salaries", """
                [{"person":"Iqpal","amountMinor":833},{"person":"Habib","amountMinor":2500},{"person":"Raseem","amountMinor":2000}]""");
        createMove(token, "2026-06-28", "EXPENSE", 192, "cleaning supplies", null);

        mockMvc.perform(get(movesUrl() + "/statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days", hasSize(2)))
                // 2026-06-01: opening 0 + cash 3000 + card (3000 + booking pay 50000) + opening move 12814 = 68814
                .andExpect(jsonPath("$.days[0].date").value("2026-06-01"))
                .andExpect(jsonPath("$.days[0].openingMinor").value(0))
                .andExpect(jsonPath("$.days[0].cashBillsMinor").value(3000))
                .andExpect(jsonPath("$.days[0].cardBillsMinor").value(53000))
                .andExpect(jsonPath("$.days[0].totalBillsMinor").value(56000))
                .andExpect(jsonPath("$.days[0].bookingsMinor").value(50000))
                .andExpect(jsonPath("$.days[0].closingMinor").value(68814))
                // 2026-06-28: opening 68814 − salary 5333 − expense 192 = 63289
                .andExpect(jsonPath("$.days[1].date").value("2026-06-28"))
                .andExpect(jsonPath("$.days[1].openingMinor").value(68814))
                .andExpect(jsonPath("$.days[1].salariesMinor").value(5333))
                .andExpect(jsonPath("$.days[1].expensesMinor").value(192))
                .andExpect(jsonPath("$.days[1].expenseNotes[0]").value("cleaning supplies"))
                .andExpect(jsonPath("$.days[1].closingMinor").value(63289))
                .andExpect(jsonPath("$.days[1].cumulativeMinor").value(63289))
                // Summary: last closing is the total balance
                .andExpect(jsonPath("$.summary.totalBalanceMinor").value(63289));
    }

    @Test
    void cashOutRequiresNotes() throws Exception {
        String token = login("editor");

        // EXPENSE / SALARY without a description → 400 (validation), not created.
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(move("2026-08-01", "EXPENSE", 100, null, null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(move("2026-08-01", "SALARY", 100, null, null)))
                .andExpect(status().isBadRequest());

        // With a description → created.
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(move("2026-08-01", "EXPENSE", 100, "cleaning supplies", null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("cleaning supplies"));
    }

    @Test
    void closingMismatchWarns() throws Exception {
        String token = login("editor");
        createMove(token, "2026-06-01", "OPENING", 1000);
        createMove(token, "2026-06-01", "CLOSING", 500); // wrong — computed closing is 1000

        mockMvc.perform(get(movesUrl() + "/statement")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].warnings[0]").exists());
    }

    @Test
    void bookingCrudAndStatus() throws Exception {
        String token = login("editor");
        String futureDue = LocalDate.now().plusMonths(3).toString();
        String pastDue = LocalDate.now().minusMonths(1).toString();

        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"D1234\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + futureDue + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plateNo").value("D1234"))
                .andExpect(jsonPath("$.status").value("PAID"));

        // Never-paid with due date in the past → DUE
        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"E5678\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + pastDue + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DUE"));

        mockMvc.perform(get(bookingsUrl() + "?status=DUE")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plateNo").value("E5678"));
    }

    @Test
    void payFlowCreatesLinkedBillAndAdvancesDates() throws Exception {
        String token = login("editor");
        String due = LocalDate.now().plusMonths(1).toString();

        MvcResult create = mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"P999\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + due + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        long bookingId = objectMapper.readTree(create.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post(bookingsUrl() + "/" + bookingId + "/pay")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountMinor\":50000,\"paymentMethod\":\"CASH\",\"paidAt\":\"" + LocalDate.now() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"))
                .andExpect(jsonPath("$.paidThroughDate").value(LocalDate.now().plusMonths(2).minusDays(1).toString()));

        // Payment history lists the linked bill
        mockMvc.perform(get(bookingsUrl() + "/" + bookingId + "/payments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].amountMinor").value(50000));
    }

    @Test
    void customIntervalValidation() throws Exception {
        String token = login("editor");
        String futureDue = LocalDate.now().plusMonths(3).toString();

        // CUSTOM without intervalMonths → 400
        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"C1\",\"monthlyRateMinor\":100,\"nextDueDate\":\"" + futureDue + "\",\"intervalType\":\"CUSTOM\"}"))
                .andExpect(status().isBadRequest());

        // CUSTOM with intervalMonths → 200
        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"C2\",\"monthlyRateMinor\":100,\"nextDueDate\":\"" + futureDue + "\",\"intervalType\":\"CUSTOM\",\"intervalMonths\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intervalMonths").value(4));
    }

    @Test
    void viewerCanReadButCannotWrite() throws Exception {
        String viewerToken = login("viewer");

        mockMvc.perform(post(billsUrl())
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bill("A1234", 5000, "CASH", "2026-08-01")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(billsUrl() + "/summary")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinor").value(0));

        mockMvc.perform(get(movesUrl() + "/statement")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk());
    }

    @Test
    void unknownBookReturns404() throws Exception {
        String token = login("editor");

        mockMvc.perform(get("/api/v1/books/999999/parking/bills/summary")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/books/999999/parking/bookings")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"D1\",\"monthlyRateMinor\":100,\"nextDueDate\":\"2026-09-01\",\"intervalType\":\"MONTHLY\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        String token = login("editor");

        mockMvc.perform(post(billsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"\",\"amountMinor\":-5,\"paymentMethod\":\"CASH\",\"billedAt\":\"2026-08-01\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-08-01\",\"type\":\"FLIP\",\"amountMinor\":100}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void billsFilterByPaymentMethod() throws Exception {
        String token = login("editor");
        String today = LocalDate.now().toString();
        createBill(token, "A1", 1000, "CASH", today);
        createBill(token, "A2", 2000, "CARD", today);

        mockMvc.perform(get(billsUrl() + "?paymentMethod=CASH")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plateNo").value("A1"))
                .andExpect(jsonPath("$[0].amountMinor").value(1000));

        mockMvc.perform(get(billsUrl() + "?paymentMethod=CARD")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].plateNo").value("A2"))
                .andExpect(jsonPath("$[0].amountMinor").value(2000));

        mockMvc.perform(get(billsUrl())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void notificationsReturnOverdueAndDueSoon() throws Exception {
        String token = login("editor");
        String overdueDate = LocalDate.now().minusDays(10).toString();
        String soonDate = LocalDate.now().plusDays(3).toString();
        String farDate = LocalDate.now().plusDays(30).toString();

        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"N1\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + overdueDate + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"N2\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + soonDate + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"N3\",\"monthlyRateMinor\":50000,\"nextDueDate\":\"" + farDate + "\",\"intervalType\":\"MONTHLY\",\"active\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/books/" + parkingBookId + "/parking/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("OVERDUE"))
                .andExpect(jsonPath("$[0].plateNo").value("N1"))
                .andExpect(jsonPath("$[1].status").value("DUE_SOON"))
                .andExpect(jsonPath("$[1].plateNo").value("N2"));
    }

    // --- helpers ---

    private String billsUrl() {
        return "/api/v1/books/" + parkingBookId + "/parking/bills";
    }

    private String movesUrl() {
        return "/api/v1/books/" + parkingBookId + "/parking/cash-moves";
    }

    private String bookingsUrl() {
        return "/api/v1/books/" + parkingBookId + "/parking/bookings";
    }

    private void createBill(String token, String plate, long amount, String method, String date)
            throws Exception {
        mockMvc.perform(post(billsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bill(plate, amount, method, date)))
                .andExpect(status().isOk());
    }

    private void createMove(String token, String date, String type, long amount) throws Exception {
        createMove(token, date, type, amount, null, null);
    }

    private void createMove(String token, String date, String type, long amount, String description,
                            String payments) throws Exception {
        String body = move(date, type, amount, description, payments);
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String bill(String plate, long amount, String method, String date) {
        return "{\"plateNo\":\"" + plate + "\",\"amountMinor\":" + amount
                + ",\"paymentMethod\":\"" + method + "\",\"billedAt\":\"" + date + "\"}";
    }

    private String move(String date, String type, long amount, String description, String payments) {
        String body = "{\"date\":\"" + date + "\",\"type\":\"" + type
                + "\",\"amountMinor\":" + amount;
        if (description != null) {
            body += ",\"description\":\"" + description + "\"";
        }
        if (payments != null) {
            body += ",\"salaryPayments\":" + payments;
        }
        return body + "}";
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
