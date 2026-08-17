package com.sandook.ledger;

import com.sandook.ledger.audit.AuditLogRepository;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDay;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.parking.ParkingBill;
import com.sandook.ledger.parking.ParkingBillRepository;
import com.sandook.ledger.parking.ParkingBooking;
import com.sandook.ledger.parking.ParkingBookingInterval;
import com.sandook.ledger.parking.ParkingBookingRepository;
import com.sandook.ledger.parking.ParkingCashMoveRepository;
import com.sandook.ledger.parking.ParkingSalaryPaymentRepository;
import com.sandook.ledger.parking.PaymentMethod;
import com.sandook.ledger.user.RefreshTokenRepository;
import com.sandook.ledger.user.Role;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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

        // Alice 833 + Bob 2500 + Charlie 2000 = 5333
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-06-28","type":"SALARY","amountMinor":5333,"description":"June salaries",
                                 "salaryPayments":[
                                   {"person":"Alice","amountMinor":833},
                                   {"person":"Bob","amountMinor":2500},
                                   {"person":"Charlie","amountMinor":2000}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.salaryPayments", hasSize(3)))
                .andExpect(jsonPath("$.salaryPayments[0].person").value("Alice"))
                .andExpect(jsonPath("$.salaryPayments[2].amountMinor").value(2000));

        // Sum mismatch → 409
        mockMvc.perform(post(movesUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-06-28","type":"SALARY","amountMinor":5333,"description":"June salaries",
                                 "salaryPayments":[{"person":"Alice","amountMinor":833}]}
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
                [{"person":"Alice","amountMinor":833},{"person":"Bob","amountMinor":2500},{"person":"Charlie","amountMinor":2000}]""");
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

    // --- Excel import/export (phase 4) ---

    @Test
    void allExportsReturnXlsx() throws Exception {
        String token = login("editor");
        String[] endpoints = {
                exportsUrl() + "/all"
        };
        for (String endpoint : endpoints) {
            MvcResult result = mockMvc.perform(get(endpoint)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                    .andReturn();
            assertTrue(result.getResponse().getContentAsByteArray().length > 0,
                    "empty export body for " + endpoint);
        }
    }

    @Test
    void viewerCannotPreviewOrCommit() throws Exception {
        String token = login("viewer");

        mockMvc.perform(multipart(importsUrl() + "/preview")
                        .file(new MockMultipartFile("file", "daybook.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                dayBookFixture(new String[]{"01/08/2026", "A1234", "", "", "50", "", "P"})))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(importsUrl() + "/commit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void previewDetectsDayBookLayout() throws Exception {
        String token = login("editor");
        byte[] fixture = dayBookFixture(
                new String[]{"01/08/2026", "A1234", "", "", "50", "", "P"},
                new String[]{"02/08/2026", "B5678", "", "", "", "70", "P"});

        mockMvc.perform(multipart(importsUrl() + "/preview")
                        .file(new MockMultipartFile("file", "daybook.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fixture))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileName").value("daybook.xlsx"))
                .andExpect(jsonPath("$.rows", hasSize(2)))
                .andExpect(jsonPath("$.rows[0].layout").value("DAY_BOOK"))
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[0].fields.date").value("2026-08-01"))
                .andExpect(jsonPath("$.rows[0].fields.amountMinor").value(5000))
                .andExpect(jsonPath("$.rows[0].fields.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.rows[1].valid").value(true))
                .andExpect(jsonPath("$.rows[1].fields.date").value("2026-08-02"))
                .andExpect(jsonPath("$.rows[1].fields.amountMinor").value(7000))
                .andExpect(jsonPath("$.rows[1].fields.paymentMethod").value("CARD"));
    }

    @Test
    void previewFlagsInvalidRows() throws Exception {
        String token = login("editor");
        byte[] fixture = dayBookFixture(
                new String[]{"01/08/2026", "A1234", "", "", "50", "", "P"},      // valid
                new String[]{"02/08/2026", "B5678", "", "", "10", "20", "P"},   // both cash+card
                new String[]{"03/08/2026", "C9012", "", "", "30", "", "X"},     // status != P
                new String[]{"04/08/2026", "", "", "", "40", "", "P"});         // missing plate

        mockMvc.perform(multipart(importsUrl() + "/preview")
                        .file(new MockMultipartFile("file", "daybook.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fixture))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(4)))
                .andExpect(jsonPath("$.rows[0].layout").value("DAY_BOOK"))
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andExpect(jsonPath("$.rows[0].errors", hasSize(0)))
                .andExpect(jsonPath("$.rows[1].valid").value(false))
                .andExpect(jsonPath("$.rows[1].errors[0]").exists())
                .andExpect(jsonPath("$.rows[2].valid").value(false))
                .andExpect(jsonPath("$.rows[2].errors[0]").exists())
                .andExpect(jsonPath("$.rows[3].valid").value(false))
                .andExpect(jsonPath("$.rows[3].errors[0]").exists());
    }

    @Test
    void commitInsertsValidAndSkipsInvalidRows() throws Exception {
        String token = login("editor");
        long before = billRepository.count();
        Long editorId = userRepository.findByUsername("editor").orElseThrow().getId();

        mockMvc.perform(post(importsUrl() + "/commit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows":[
                                  {"rowNo":2,"sheet":"Day Book","layout":"DAY_BOOK","fields":{"date":"2026-08-01",
                                    "plateNo":"Z1001","amountMinor":4500,"paymentMethod":"CASH","paymentStatus":"p"},
                                   "valid":true,"errors":[]},
                                  {"rowNo":3,"sheet":"Day Book","layout":"DAY_BOOK","fields":{"date":"2026-08-02",
                                    "plateNo":"Z1002","amountMinor":4500,"paymentMethod":"CASH","paymentStatus":"x"},
                                   "valid":false,"errors":["payment status is not P — row skipped"]}
                                ]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.skipped").value(1));

        assertTrue(billRepository.count() == before + 1, "expected exactly one new bill");
        ParkingBill bill = billRepository.findAll().stream()
                .filter(b -> b.getPlateNo().equals("Z1001"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("imported bill Z1001 not found"));
        assertTrue(bill.getAmountMinor() == 4500, "amountMinor persisted");
        assertTrue(bill.getPaymentMethod() == PaymentMethod.CASH, "payment method persisted");
        assertTrue(bill.getBilledAt().equals(LocalDate.of(2026, 8, 1)), "billedAt persisted");
        assertTrue(editorId.equals(bill.getEnteredBy()), "enteredBy persisted");
    }

    @Test
    void cashDepositDuplicateDateDedupesInBatch() throws Exception {
        String token = login("editor");
        long before = cashDayRepository.count();
        byte[] fixture = cashDepositFixture(
                new String[]{"01/08/2026", "100", "", "", "80", "first", "", ""},
                new String[]{"01/08/2026", "50", "", "", "30", "second", "", ""});

        // Preview: same-date rows are merged into one.
        MvcResult preview = mockMvc.perform(multipart(importsUrl() + "/preview")
                        .file(new MockMultipartFile("file", "deposit.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", fixture))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].layout").value("CASH_DEPOSIT"))
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andReturn();

        // Commit the preview row: 1 merged row inserted.
        String rows = objectMapper.readTree(preview.getResponse().getContentAsString()).get("rows").toString();
        mockMvc.perform(post(importsUrl() + "/commit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":" + rows + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        assertTrue(cashDayRepository.count() == before + 1, "expected exactly one new cash day");
        CashDay day = cashDayRepository.findAll().stream()
                .filter(d -> d.getDate().equals(LocalDate.of(2026, 8, 1)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("cash day 2026-08-01 not found"));
        assertTrue(day.getSalesMinor() == 15000, "merged sales persisted (100+50)");
        assertTrue(day.getDepositMinor() == 11000, "merged deposit persisted (80+30)");
    }

    @Test
    void bookingExportRoundTripsThroughImport() throws Exception {
        String token = login("editor");

        // Source booking with a CUSTOM interval, future due -> status PAID.
        mockMvc.perform(post(bookingsUrl())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"plateNo\":\"RT123\",\"monthlyRateMinor\":50000," +
                                "\"nextDueDate\":\"2026-10-01\",\"intervalType\":\"CUSTOM\"," +
                                "\"intervalMonths\":4,\"active\":true}"))
                .andExpect(status().isOk());
        long before = bookingRepository.count();

        byte[] workbook = mockMvc.perform(get(exportsUrl() + "/all")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(workbook.length > 0, "bookings export empty");

        MvcResult preview = mockMvc.perform(multipart(importsUrl() + "/preview")
                        .file(new MockMultipartFile("file", "bookings.xlsx",
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", workbook))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].layout").value("BOOKING_SHEET"))
                .andExpect(jsonPath("$.rows[0].valid").value(true))
                .andReturn();

        String rows = objectMapper.readTree(preview.getResponse().getContentAsString()).get("rows").toString();
        mockMvc.perform(post(importsUrl() + "/commit")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rows\":" + rows + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(1))
                .andExpect(jsonPath("$.skipped").value(0));

        assertTrue(bookingRepository.count() == before + 1, "expected exactly one new booking");
        ParkingBooking imported = bookingRepository.findAll().stream()
                .filter(b -> b.getPlateNo().equals("RT123") && b.getIntervalType() == ParkingBookingInterval.CUSTOM)
                .findFirst()
                .orElseThrow(() -> new AssertionError("imported booking RT123 not found"));
        assertTrue(imported.getIntervalMonths() != null && imported.getIntervalMonths() == 4,
                "intervalMonths parsed from term");
        assertTrue(imported.isActive(), "active parsed from payment status");
        assertTrue(imported.getMonthlyRateMinor() == 50000, "monthly rate round-tripped");
        assertTrue(imported.getNextDueDate().equals(LocalDate.of(2026, 10, 1)), "next due date round-tripped");
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

    private String exportsUrl() {
        return "/api/v1/books/" + parkingBookId + "/exports";
    }

    private String importsUrl() {
        return "/api/v1/books/" + parkingBookId + "/imports";
    }

    static byte[] workbookBytes(Consumer<XSSFWorkbook> fill) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fill.accept(wb);
            wb.write(out);
            return out.toByteArray();
        }
    }

    /** Day-book fixture matching the original layout: DATE | CAR NUMBER | DURATION | TERM | CASH | CARD | PAYMENT STATUS. */
    private static byte[] dayBookFixture(String[]... rows) throws IOException {
        return workbookBytes(wb -> {
            Sheet sheet = wb.createSheet("Day Book");
            String[] headers = {"DATE", "CAR NUMBER", "DURATION", "TERM", "CASH", "CARD", "PAYMENT STATUS"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (String[] row : rows) {
                Row x = sheet.createRow(r++);
                for (int i = 0; i < row.length; i++) {
                    x.createCell(i).setCellValue(row[i]);
                }
            }
        });
    }

    /** Cash-deposit fixture: Date | Sales Amount | Extra Amount take fr | Withdraw | Deposit Amount | Deposit Remarks | Reference/Receipt No | Notes. */
    private static byte[] cashDepositFixture(String[]... rows) throws IOException {
        return workbookBytes(wb -> {
            Sheet sheet = wb.createSheet("Jan 2026");
            String[] headers = {"Date", "Sales Amount", "Extra Amount take fr", "Withdraw",
                    "Deposit Amount", "Deposit Remarks", "Reference/Receipt No", "Notes"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }
            int r = 1;
            for (String[] row : rows) {
                Row x = sheet.createRow(r++);
                for (int i = 0; i < row.length; i++) {
                    x.createCell(i).setCellValue(row[i]);
                }
            }
        });
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
