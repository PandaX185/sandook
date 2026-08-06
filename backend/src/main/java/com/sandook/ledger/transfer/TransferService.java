package com.sandook.ledger.transfer;

import com.sandook.ledger.audit.AuditService;
import com.sandook.ledger.book.Book;
import com.sandook.ledger.book.BookRepository;
import com.sandook.ledger.cash.CashDay;
import com.sandook.ledger.cash.CashDayRepository;
import com.sandook.ledger.common.ConflictException;
import com.sandook.ledger.common.NotFoundException;
import com.sandook.ledger.parking.ParkingCashMove;
import com.sandook.ledger.parking.ParkingCashMoveRepository;
import com.sandook.ledger.parking.ParkingCashMoveType;
import com.sandook.ledger.user.User;
import com.sandook.ledger.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final ParkingCashMoveRepository parkingMoveRepository;
    private final CashDayRepository cashDayRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public TransferService(TransferRepository transferRepository,
                           ParkingCashMoveRepository parkingMoveRepository,
                           CashDayRepository cashDayRepository,
                           BookRepository bookRepository,
                           UserRepository userRepository,
                           AuditService auditService) {
        this.transferRepository = transferRepository;
        this.parkingMoveRepository = parkingMoveRepository;
        this.cashDayRepository = cashDayRepository;
        this.bookRepository = bookRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<TransferResponse> list(Long bookId, LocalDate from, LocalDate to) {
        List<Transfer> transfers;
        if (bookId != null && from != null && to != null) {
            transfers = transferRepository
                    .findAllByFromBookIdOrToBookIdAndDateBetweenOrderByDateAscIdAsc(bookId, bookId, from, to);
        } else if (bookId != null) {
            transfers = transferRepository.findAllByFromBookIdOrToBookIdOrderByDateAscIdAsc(bookId, bookId);
        } else if (from != null && to != null) {
            transfers = transferRepository.findAllByDateBetweenOrderByDateAscIdAsc(from, to);
        } else {
            transfers = transferRepository.findAllByOrderByDateAscIdAsc();
        }
        return transfers.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public TransferResponse get(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer not found: " + id));
        return toResponse(transfer);
    }

    /**
     * Create a transfer. With {@code linkParkingMove=true} the same call also:
     * 1. creates a TRANSFER_TO_SHOP parking cash move on the from-book, and
     * 2. adds the amount to the to-book's cash day {@code extra_minor} (auto-creating
     *    the day row if missing) — the 12,500 parking→shop flow is now one click.
     */
    @Transactional
    public TransferResponse create(TransferRequest request, String username) {
        Book fromBook = requireBook(request.fromBookId());
        Book toBook = requireBook(request.toBookId());
        if (request.fromBookId().equals(request.toBookId())) {
            throw new ConflictException("Transfer must be between two different books");
        }
        if (Boolean.TRUE.equals(request.linkParkingMove()) && !"Parking".equals(fromBook.getName())) {
            throw new ConflictException("Linked parking moves require the from-book to be named 'Parking'");
        }

        Transfer transfer = new Transfer();
        transfer.setFromBookId(request.fromBookId());
        transfer.setToBookId(request.toBookId());
        transfer.setDate(request.date());
        transfer.setAmountMinor(request.amountMinor());
        transfer.setCurrencyCode(fromBook.getCurrencyCode());
        transfer.setRef(request.ref());
        transfer.setEnteredBy(userId(username));
        transferRepository.save(transfer);

        if (Boolean.TRUE.equals(request.linkParkingMove())) {
            Long actor = transfer.getEnteredBy();
            ParkingCashMove move = createLinkedMove(transfer, actor);
            auditService.record("CREATE", "parking_cash_move", move.getId(), null, auditService.toNode(move));
            applyLinkedExtra(toBook.getId(), transfer.getDate(), transfer.getAmountMinor(), actor);
        }
        TransferResponse response = toResponse(transfer);
        auditService.record("CREATE", "transfer", transfer.getId(), null, response);
        return response;
    }

    /**
     * Update amount/date/ref. Books never change. A linked transfer reverses
     * both links first, then re-applies them with the new values — same
     * reversal pattern as the petty cash top-up.
     */
    @Transactional
    public TransferResponse update(Long id, TransferUpdateRequest request) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer not found: " + id));

        ParkingCashMove linkedMove = linkedMove(transfer);
        boolean linked = linkedMove != null;

        tools.jackson.databind.JsonNode oldValue = auditService.toNode(transfer);
        if (linked) {
            // Reverse old linkage.
            auditService.record("DELETE", "parking_cash_move", linkedMove.getId(),
                    auditService.toNode(linkedMove), null);
            parkingMoveRepository.delete(linkedMove);
            applyLinkedExtra(transfer.getToBookId(), transfer.getDate(),
                    -transfer.getAmountMinor(), transfer.getEnteredBy());
        }

        transfer.setDate(request.date());
        transfer.setAmountMinor(request.amountMinor());
        transfer.setRef(request.ref());
        transferRepository.save(transfer);

        if (linked) {
            Long actor = transfer.getEnteredBy();
            ParkingCashMove move = createLinkedMove(transfer, actor);
            auditService.record("CREATE", "parking_cash_move", move.getId(), null, auditService.toNode(move));
            applyLinkedExtra(transfer.getToBookId(), transfer.getDate(), transfer.getAmountMinor(), actor);
        }
        TransferResponse response = toResponse(transfer);
        auditService.record("UPDATE", "transfer", transfer.getId(), oldValue, response);
        return response;
    }

    @Transactional
    public void delete(Long id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Transfer not found: " + id));

        ParkingCashMove linkedMove = linkedMove(transfer);
        if (linkedMove != null) {
            auditService.record("DELETE", "parking_cash_move", linkedMove.getId(),
                    auditService.toNode(linkedMove), null);
            parkingMoveRepository.delete(linkedMove);
            applyLinkedExtra(transfer.getToBookId(), transfer.getDate(),
                    -transfer.getAmountMinor(), transfer.getEnteredBy());
        }
        auditService.record("DELETE", "transfer", transfer.getId(), auditService.toNode(transfer), null);
        transferRepository.delete(transfer);
    }

    /** The TRANSFER_TO_SHOP move this transfer auto-created, if any. */
    private ParkingCashMove linkedMove(Transfer transfer) {
        return parkingMoveRepository.findByTransferId(transfer.getId()).orElse(null);
    }

    /** The to-book cash day carrying this transfer's extra, if any. */
    private CashDay linkedCashDay(Transfer transfer) {
        return cashDayRepository.findByBookIdAndDate(transfer.getToBookId(), transfer.getDate()).orElse(null);
    }

    /** Full response with live linkage: the auto-created parking move + shop extra. */
    private TransferResponse toResponse(Transfer transfer) {
        ParkingCashMove move = linkedMove(transfer);
        CashDay day = move == null ? null : linkedCashDay(transfer);
        return TransferResponse.from(transfer, move != null,
                move == null ? null : move.getId(),
                day == null ? null : day.getId(),
                day == null ? null : day.getExtraMinor());
    }

    private ParkingCashMove createLinkedMove(Transfer transfer, Long actor) {
        ParkingCashMove move = new ParkingCashMove();
        move.setBookId(transfer.getFromBookId());
        move.setDate(transfer.getDate());
        move.setType(ParkingCashMoveType.TRANSFER_TO_SHOP);
        move.setAmountMinor(transfer.getAmountMinor());
        move.setDescription(transfer.getRef() == null ? "Transfer to shop" : "Transfer to shop (" + transfer.getRef() + ")");
        move.setEnteredBy(actor);
        move.setTransferId(transfer.getId());
        parkingMoveRepository.save(move);
        return move;
    }

    /**
     * Adjust the to-book's cash day extra_minor for a book+date by a delta.
     * Creates the day row when missing, prunes it when it becomes all-zero —
     * identical lifecycle to the petty cash linked withdraw.
     */
    private LinkedCashDay applyLinkedExtra(Long bookId, LocalDate date, long delta, Long actor) {
        CashDay day = cashDayRepository.findByBookIdAndDate(bookId, date).orElse(null);
        if (day == null) {
            if (delta <= 0) {
                return null;
            }
            day = new CashDay();
            day.setBookId(bookId);
            day.setDate(date);
            day.setExtraMinor(delta);
            day.setEnteredBy(actor);
            day.setNotes("Auto-created: parking transfer");
            cashDayRepository.save(day);
            return new LinkedCashDay(day.getId(), day.getExtraMinor());
        }
        long newExtra = day.getExtraMinor() + delta;
        if (newExtra < 0) {
            throw new ConflictException("Cash day " + date + " has extra " + day.getExtraMinor()
                    + " — cannot unlink " + (-delta) + " from it");
        }
        if (newExtra == 0 && day.getSalesMinor() == 0 && day.getWithdrawMinor() == 0 && day.getDepositMinor() == 0) {
            cashDayRepository.delete(day);
            return null;
        }
        day.setExtraMinor(newExtra);
        day.setUpdatedAt(Instant.now());
        cashDayRepository.save(day);
        return new LinkedCashDay(day.getId(), day.getExtraMinor());
    }

    private Book requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new NotFoundException("Book not found: " + bookId));
    }

    private Long userId(String username) {
        return userRepository.findByUsername(username).map(User::getId).orElse(null);
    }

    private record LinkedCashDay(Long dayId, Long dayExtraMinor) {
    }
}
