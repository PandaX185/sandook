package com.sandook.ledger.cash;

import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.common.ConflictException;
import com.sandook.ledger.common.NotFoundException;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class CashDayService {

    private final CashDayRepository cashDayRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public CashDayService(CashDayRepository cashDayRepository,
                          BookRepository bookRepository,
                          UserRepository userRepository) {
        this.cashDayRepository = cashDayRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CashDayResponse> list(Long bookId) {
        requireBook(bookId);
        List<CashDayBalanceRow> rows = cashDayRepository.findWithBalanceByBookId(bookId);
        List<CashDayResponse> responses = new ArrayList<>(rows.size());
        long previousBalance = 0;
        for (CashDayBalanceRow row : rows) {
            long impliedCash = previousBalance + row.getSalesMinor() + row.getExtraMinor() - row.getWithdrawMinor();
            responses.add(CashDayResponse.from(row, depositWarnings(row.getDepositMinor(), impliedCash)));
            previousBalance = row.getBalanceMinor();
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public CashDayResponse get(Long bookId, Long id) {
        requireBook(bookId);
        CashDay day = cashDayRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Cash day not found: book " + bookId + ", id " + id));
        long opening = cashDayRepository.sumNetBefore(bookId, day.getDate());
        long impliedCash = opening + day.getSalesMinor() + day.getExtraMinor() - day.getWithdrawMinor();
        long balance = impliedCash - day.getDepositMinor();
        return CashDayResponse.from(day, balance, depositWarnings(day.getDepositMinor(), impliedCash));
    }

    @Transactional
    public CashDayResponse create(Long bookId, CashDayRequest request, String username) {
        requireBook(bookId);
        if (cashDayRepository.existsByBookIdAndDate(bookId, request.date())) {
            throw new ConflictException("A cash day already exists for book " + bookId + " on " + request.date());
        }
        CashDay day = new CashDay();
        apply(day, request);
        day.setBookId(bookId);
        day.setEnteredBy(userId(username));
        cashDayRepository.save(day);

        long opening = cashDayRepository.sumNetBefore(bookId, day.getDate());
        long impliedCash = opening + day.getSalesMinor() + day.getExtraMinor() - day.getWithdrawMinor();
        long balance = impliedCash - day.getDepositMinor();
        return CashDayResponse.from(day, balance, depositWarnings(day.getDepositMinor(), impliedCash));
    }

    @Transactional
    public CashDayResponse update(Long bookId, Long id, CashDayRequest request) {
        requireBook(bookId);
        CashDay day = cashDayRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Cash day not found: book " + bookId + ", id " + id));
        if (!day.getDate().equals(request.date())
                && cashDayRepository.existsByBookIdAndDate(bookId, request.date())) {
            throw new ConflictException("A cash day already exists for book " + bookId + " on " + request.date());
        }
        apply(day, request);
        day.setUpdatedAt(java.time.Instant.now());
        cashDayRepository.save(day);

        long opening = cashDayRepository.sumNetBefore(bookId, day.getDate());
        long impliedCash = opening + day.getSalesMinor() + day.getExtraMinor() - day.getWithdrawMinor();
        long balance = impliedCash - day.getDepositMinor();
        return CashDayResponse.from(day, balance, depositWarnings(day.getDepositMinor(), impliedCash));
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        requireBook(bookId);
        CashDay day = cashDayRepository.findByBookIdAndId(bookId, id)
                .orElseThrow(() -> new NotFoundException("Cash day not found: book " + bookId + ", id " + id));
        cashDayRepository.delete(day);
    }

    private void apply(CashDay day, CashDayRequest request) {
        day.setDate(request.date());
        day.setSalesMinor(request.salesMinor());
        day.setExtraMinor(request.extraMinor());
        day.setWithdrawMinor(request.withdrawMinor());
        day.setDepositMinor(request.depositMinor());
        day.setDepositRemarks(request.depositRemarks());
        day.setRef(request.ref());
        day.setNotes(request.notes());
    }

    /**
     * Deposit sanity check (kills the silent-drift bug class): when a deposit is
     * recorded, it should match the cash on hand right before depositing
     * (opening + sales + extra − withdraw). Mismatch is a warning, not an error —
     * a leftover float is legitimate, but the user should see it.
     */
    private List<String> depositWarnings(long depositMinor, long impliedCashMinor) {
        List<String> warnings = new ArrayList<>();
        if (depositMinor > 0 && depositMinor != impliedCashMinor) {
            warnings.add("Deposit " + depositMinor + " does not match cash on hand before deposit (" + impliedCashMinor + ")");
        }
        return warnings;
    }

    private void requireBook(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new NotFoundException("Book not found: " + bookId);
        }
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }
}
