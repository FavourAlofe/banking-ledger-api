package com.favour.ledger.repository;

import com.favour.ledger.model.LedgerTransaction;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

    @EntityGraph(attributePaths = {"entries", "entries.account"})
    Optional<LedgerTransaction> findByIdempotencyKey(UUID idempotencyKey);

    @Override
    @EntityGraph(attributePaths = {"entries", "entries.account"})
    Optional<LedgerTransaction> findById(UUID id);

    @EntityGraph(attributePaths = {"entries", "entries.account"})
    List<LedgerTransaction> findDistinctByEntries_Account_AccountNumberOrderByCreatedAtDesc(String accountNumber);
}
