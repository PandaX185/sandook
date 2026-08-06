package com.sandook.ledger.parking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "parking_bookings")
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

    @Column(name = "renewal_month", nullable = false)
    private LocalDate renewalMonth;

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

    public LocalDate getRenewalMonth() {
        return renewalMonth;
    }

    public void setRenewalMonth(LocalDate renewalMonth) {
        this.renewalMonth = renewalMonth;
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
