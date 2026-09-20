package com.favour.ledger.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, unique = true, updatable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 255, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.PERSIST, orphanRemoval = false)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected LedgerTransaction() {
    }

    public LedgerTransaction(UUID idempotencyKey, String description) {
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotency key is required");
        this.description = Objects.requireNonNull(description, "description is required");
    }

    public static LedgerTransaction transfer(
            UUID idempotencyKey,
            String description,
            Account source,
            Account destination,
            BigDecimal amount
    ) {
        LedgerTransaction transaction = new LedgerTransaction(idempotencyKey, description);
        transaction.addEntry(new LedgerEntry(source, amount, EntryType.DEBIT));
        transaction.addEntry(new LedgerEntry(destination, amount, EntryType.CREDIT));
        transaction.assertBalanced();
        return transaction;
    }

    public void addEntry(LedgerEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        entry.assignTransaction(this);
        this.entries.add(entry);
    }

    public void assertBalanced() {
        BigDecimal debits = sum(EntryType.DEBIT);
        BigDecimal credits = sum(EntryType.CREDIT);
        if (debits.compareTo(credits) != 0) {
            throw new IllegalStateException(
                    "Unbalanced ledger transaction: debits=" + debits + " credits=" + credits
            );
        }
        if (debits.signum() <= 0) {
            throw new IllegalStateException("Ledger transaction must contain at least one non-zero entry");
        }
    }

    private BigDecimal sum(EntryType type) {
        return entries.stream()
                .filter(entry -> entry.getType() == type)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO.setScale(Account.MONEY_SCALE, Account.MONEY_ROUNDING), BigDecimal::add);
    }

    public UUID getId() {
        return id;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<LedgerEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
        assertBalanced();
    }
}
