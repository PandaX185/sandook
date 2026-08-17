package com.sandook.ledger.parking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "parking_bookings",
       indexes = @Index(name = "idx_parking_bookings_book", columnList = "book_id, next_due_date"))
public class ParkingBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "book_id", nullable = false)
    private Long bookId;

    @Column(name = "plate_no", nullable = false, length = 20)
    private String plateNo;

    @Column(name = "monthly_rate_minor", nullable = false)
    private long monthlyRateMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_type", nullable = false, length = 20)
    private ParkingBookingInterval intervalType = ParkingBookingInterval.MONTHLY;

    @Column(name = "interval_months")
    private Integer intervalMonths;

    @Column(name = "next_due_date", nullable = false)
    private LocalDate nextDueDate;

    @Column(name = "paid_through_date")
    private LocalDate paidThroughDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "entered_by")
    private Long enteredBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getBookId() {
        return bookId;
    }

    public void setBookId(Long bookId) {
        this.bookId = bookId;
    }

    public String getPlateNo() {
        return plateNo;
    }

    public void setPlateNo(String plateNo) {
        this.plateNo = plateNo;
    }

    public long getMonthlyRateMinor() {
        return monthlyRateMinor;
    }

    public void setMonthlyRateMinor(long monthlyRateMinor) {
        this.monthlyRateMinor = monthlyRateMinor;
    }

    public ParkingBookingInterval getIntervalType() {
        return intervalType;
    }

    public void setIntervalType(ParkingBookingInterval intervalType) {
        this.intervalType = intervalType;
    }

    public Integer getIntervalMonths() {
        return intervalMonths;
    }

    public void setIntervalMonths(Integer intervalMonths) {
        this.intervalMonths = intervalMonths;
    }

    public LocalDate getNextDueDate() {
        return nextDueDate;
    }

    public void setNextDueDate(LocalDate nextDueDate) {
        this.nextDueDate = nextDueDate;
    }

    public LocalDate getPaidThroughDate() {
        return paidThroughDate;
    }

    public void setPaidThroughDate(LocalDate paidThroughDate) {
        this.paidThroughDate = paidThroughDate;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
}
