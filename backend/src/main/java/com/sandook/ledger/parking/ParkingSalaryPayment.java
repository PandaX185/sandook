package com.sandook.ledger.parking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_salary_payments")
public class ParkingSalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "move_id", nullable = false)
    private Long moveId;

    @Column(nullable = false, length = 100)
    private String person;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    public Long getId() {
        return id;
    }

    public Long getMoveId() {
        return moveId;
    }

    public void setMoveId(Long moveId) {
        this.moveId = moveId;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public long getAmountMinor() {
        return amountMinor;
    }

    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }
}
