package com.sandook.ledger.pettycash;

import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDay;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.common.ConflictException;
import com.sandook.ledger.common.NotFoundException;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PettyCashService {

    private final PettyCashTransactionRepository pettyCashRepository;
    private final CashDayRepository cashDayRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;

    public PettyCashService(PettyCashTransactionRepository pettyCashRepository,
                            CashDayRepository cashDayRepository,
                            BookRepository bookRepository,
                            UserRepository userRepository) {
        this.pettyCashRepository = pettyCashRepository;
        this.cashDayRepository = cashDayRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<PettyCashTransactionResponse> list(Long bookId) {
        requireBook(bookId);
        List<PettyCashTransaction> txs = pettyCashRepository.findAllByBookIdOrderByDateAscIdAsc(bookId);
        Map<Long, Long> balances = new HashMap<>();
        for (PettyCashBalancePoint p : pettyCashRepository.findRunningBalances(bookId)) {
            balances.put(p.getId(), p.getBalanceMinor());
        }
        return txs.stream()
                .map(tx -> PettyCashTransactionResponse.from(tx, balances.getOrDefault(tx.getId(), 0L), null, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public long balance(Long bookId) {
        requireBook(bookId);
        return pettyCashRepository.totalBalance(bookId);
    }

    @Transactional(readOnly = true)
    public long balanceAsOf(Long bookId, LocalDate date) {
        requireBook(bookId);
        return pettyCashRepository.balanceAsOf(bookId, date);
    }

    /**
     * Create a ledger entry. A PUT (money put INTO petty cash, i.e. taken from
     * the till) automatically links to the cash day: it adds to that day's
     * withdraw on the Shop book — one entry updates both ledgers, killing the
     * manual double-entry that caused the Excel mismatches.
     */
    @Transactional
    public PettyCashTransactionResponse create(Long bookId, PettyCashTransactionRequest request, String username) {
        Book book = requireBook(bookId);
        PettyCashTransaction tx = new PettyCashTransaction();
        tx.setBookId(bookId);
        tx.setDate(request.date());
        tx.setDescription(request.description().trim());
        tx.setType(request.type());
        tx.setAmountMinor(request.amountMinor());
        tx.setCurrencyCode(book.getCurrencyCode());
        tx.setEnteredBy(userId(username));
        pettyCashRepository.save(tx);

        LinkedCashDay link = null;
        if (request.type() == PettyCashType.PUT) {
            link = applyLinkedWithdraw(bookId, request.date(), request.amountMinor(), userId(username));
        }
        long balance = pettyCashRepository.totalBalance(bookId);
        return PettyCashTransactionResponse.from(tx, balance,
                link == null ? null : link.dayId(), link == null ? null : link.dayWithdrawMinor());
    }

    @Transactional
    public PettyCashTransactionResponse update(Long bookId, Long id, PettyCashTransactionRequest request) {
        requireBook(bookId);
        PettyCashTransaction tx = pettyCashRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Petty cash transaction not found: " + id));
        Long actor = tx.getEnteredBy();

        // Reverse the old linkage before applying the new state (PUT → TAKE or
        // amount/date changes must move the withdraw on the cash day).
        if (tx.getType() == PettyCashType.PUT) {
            applyLinkedWithdraw(bookId, tx.getDate(), -tx.getAmountMinor(), actor);
        }
        tx.setDate(request.date());
        tx.setDescription(request.description().trim());
        tx.setType(request.type());
        tx.setAmountMinor(request.amountMinor());
        pettyCashRepository.save(tx);

        LinkedCashDay link = null;
        if (request.type() == PettyCashType.PUT) {
            link = applyLinkedWithdraw(bookId, request.date(), request.amountMinor(), actor);
        }
        long balance = pettyCashRepository.totalBalance(bookId);
        return PettyCashTransactionResponse.from(tx, balance,
                link == null ? null : link.dayId(), link == null ? null : link.dayWithdrawMinor());
    }

    @Transactional
    public void delete(Long bookId, Long id) {
        requireBook(bookId);
        PettyCashTransaction tx = pettyCashRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Petty cash transaction not found: " + id));
        if (tx.getType() == PettyCashType.PUT) {
            applyLinkedWithdraw(bookId, tx.getDate(), -tx.getAmountMinor(), tx.getEnteredBy());
        }
        pettyCashRepository.delete(tx);
    }

    /**
     * Adjust the cash day's withdraw for a book+date by a delta (fils).
     * Creates the day row when missing (top-up on a day with no sheet yet),
     * prunes it when it becomes all-zero (fully reversed top-up).
     */
    private LinkedCashDay applyLinkedWithdraw(Long bookId, LocalDate date, long delta, Long actor) {
        CashDay day = cashDayRepository.findByBookIdAndDate(bookId, date).orElse(null);
        if (day == null) {
            if (delta <= 0) {
                // Reversing a link whose day row is already gone — nothing to adjust.
                return null;
            }
            day = new CashDay();
            day.setBookId(bookId);
            day.setDate(date);
            day.setWithdrawMinor(delta);
            day.setEnteredBy(actor);
            day.setNotes("Auto-created: petty cash top-up");
            cashDayRepository.save(day);
            return new LinkedCashDay(day.getId(), day.getWithdrawMinor());
        }
        long newWithdraw = day.getWithdrawMinor() + delta;
        if (newWithdraw < 0) {
            throw new ConflictException("Cash day " + date + " has withdraw " + day.getWithdrawMinor()
                    + " — cannot unlink " + (-delta) + " from it");
        }
        if (newWithdraw == 0 && day.getSalesMinor() == 0 && day.getExtraMinor() == 0 && day.getDepositMinor() == 0) {
            cashDayRepository.delete(day);
            return null;
        }
        day.setWithdrawMinor(newWithdraw);
        day.setUpdatedAt(Instant.now());
        cashDayRepository.save(day);
        return new LinkedCashDay(day.getId(), day.getWithdrawMinor());
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }

    private record LinkedCashDay(Long dayId, Long dayWithdrawMinor) {
    }
}
