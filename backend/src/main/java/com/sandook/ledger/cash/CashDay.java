package com.sandook.ledger.cash;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "cash_days",
       uniqueConstraints = @UniqueConstraint(columnNames = {"book_id", "date"}),
       indexes = @Index(name = "idx_cash_days_book_date", columnList = "book_id, date"))
public class CashDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "sales_minor", nullable = false)
    private long salesMinor;

    @Column(name = "extra_minor", nullable = false)
    private long extraMinor;

    @Column(name = "withdraw_minor", nullable = false)
    private long withdrawMinor;

    @Column(name = "deposit_minor", nullable = false)
    private long depositMinor;

    @Column(name = "deposit_remarks", length = 255)
    private String depositRemarks;

    @Column(name = "ref", length = 100)
    private String ref;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "entered_by")
    private Long enteredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public long getSalesMinor() {
        return salesMinor;
    }

    public void setSalesMinor(long salesMinor) {
        this.salesMinor = salesMinor;
    }

    public long getExtraMinor() {
        return extraMinor;
    }

    public void setExtraMinor(long extraMinor) {
        this.extraMinor = extraMinor;
    }

    public long getWithdrawMinor() {
        return withdrawMinor;
    }

    public void setWithdrawMinor(long withdrawMinor) {
        this.withdrawMinor = withdrawMinor;
    }

    public long getDepositMinor() {
        return depositMinor;
    }

    public void setDepositMinor(long depositMinor) {
        this.depositMinor = depositMinor;
    }

    public String getDepositRemarks() {
        return depositRemarks;
    }

    public void setDepositRemarks(String depositRemarks) {
        this.depositRemarks = depositRemarks;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getEnteredBy() {
        return enteredBy;
    }

    public void setEnteredBy(Long enteredBy) {
        this.enteredBy = enteredBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
