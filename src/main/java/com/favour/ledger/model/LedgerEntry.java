package com.favour.ledger.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false, updatable = false)
    private LedgerTransaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false, updatable = false)
    private Account account;

    @Column(nullable = false, precision = 19, scale = Account.MONEY_SCALE, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private EntryType type;

    protected LedgerEntry() {
    }

    public LedgerEntry(Account account, BigDecimal amount, EntryType type) {
        this.account = Objects.requireNonNull(account, "account is required");
        this.type = Objects.requireNonNull(type, "entry type is required");
        this.amount = requirePositive(amount);
    }

    void assignTransaction(LedgerTransaction transaction) {
        this.transaction = transaction;
    }

    public UUID getId() {
        return id;
    }

    public LedgerTransaction getTransaction() {
        return transaction;
    }

    public Account getAccount() {
        return account;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public EntryType getType() {
        return type;
    }

    @PrePersist
    void onCreate() {
        this.amount = requirePositive(this.amount);
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("amount is required");
        }
        BigDecimal scaled = value.setScale(Account.MONEY_SCALE, Account.MONEY_ROUNDING);
        if (scaled.signum() <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        return scaled;
    }
}
