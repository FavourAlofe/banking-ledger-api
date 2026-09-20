package com.favour.ledger.service;

import com.favour.ledger.exception.AccountNotFoundException;
import com.favour.ledger.exception.IdempotencyConflictException;
import com.favour.ledger.exception.InvalidTransferException;
import com.favour.ledger.exception.TransactionNotFoundException;
import com.favour.ledger.model.Account;
import com.favour.ledger.model.EntryType;
import com.favour.ledger.model.LedgerEntry;
import com.favour.ledger.model.LedgerTransaction;
import com.favour.ledger.repository.AccountRepository;
import com.favour.ledger.repository.LedgerTransactionRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;
    private final TransactionTemplate transactionTemplate;

    public TransferService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository,
            PlatformTransactionManager transactionManager
    ) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public LedgerTransaction transfer(
            UUID idempotencyKey,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String description
    ) {
        BigDecimal scaledAmount = scale(amount);
        if (scaledAmount.signum() <= 0) {
            throw new InvalidTransferException("Transfer amount must be greater than zero");
        }
        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new InvalidTransferException("Source and destination accounts must differ");
        }

        LedgerTransaction existing = ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            assertReusable(existing, sourceAccountNumber, destinationAccountNumber, scaledAmount);
            return existing;
        }

        try {
            return transactionTemplate.execute(status ->
                    executeTransfer(
                            idempotencyKey,
                            sourceAccountNumber,
                            destinationAccountNumber,
                            scaledAmount,
                            description
                    )
            );
        } catch (DataIntegrityViolationException ex) {
            LedgerTransaction raced = ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> ex);
            assertReusable(raced, sourceAccountNumber, destinationAccountNumber, scaledAmount);
            return raced;
        }
    }

    public LedgerTransaction getById(UUID id) {
        return ledgerTransactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    private LedgerTransaction executeTransfer(
            UUID idempotencyKey,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount,
            String description
    ) {
        LedgerTransaction existing = ledgerTransactionRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
        if (existing != null) {
            assertReusable(existing, sourceAccountNumber, destinationAccountNumber, amount);
            return existing;
        }

        Account source;
        Account destination;
        // Lock in account-number order so A→B and B→A cannot deadlock.
        if (sourceAccountNumber.compareTo(destinationAccountNumber) < 0) {
            source = lock(sourceAccountNumber);
            destination = lock(destinationAccountNumber);
        } else {
            destination = lock(destinationAccountNumber);
            source = lock(sourceAccountNumber);
        }

        AccountService.requireActive(source);
        AccountService.requireActive(destination);
        AccountService.requireFunds(source, amount);

        source.debit(amount);
        destination.credit(amount);

        String resolvedDescription = (description == null || description.isBlank())
                ? "Transfer " + sourceAccountNumber + " -> " + destinationAccountNumber
                : description;

        LedgerTransaction transaction = LedgerTransaction.transfer(
                idempotencyKey,
                resolvedDescription,
                source,
                destination,
                amount
        );
        return ledgerTransactionRepository.save(transaction);
    }

    private Account lock(String accountNumber) {
        return accountRepository.lockByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private static void assertReusable(
            LedgerTransaction existing,
            String sourceAccountNumber,
            String destinationAccountNumber,
            BigDecimal amount
    ) {
        LedgerEntry debit = entry(existing, EntryType.DEBIT);
        LedgerEntry credit = entry(existing, EntryType.CREDIT);
        boolean samePayload = debit.getAccount().getAccountNumber().equals(sourceAccountNumber)
                && credit.getAccount().getAccountNumber().equals(destinationAccountNumber)
                && debit.getAmount().compareTo(amount) == 0
                && credit.getAmount().compareTo(amount) == 0;
        if (!samePayload) {
            throw new IdempotencyConflictException();
        }
    }

    private static LedgerEntry entry(LedgerTransaction transaction, EntryType type) {
        List<LedgerEntry> matches = transaction.getEntries().stream()
                .filter(entry -> entry.getType() == type)
                .sorted(Comparator.comparing(entry -> entry.getAccount().getAccountNumber()))
                .toList();
        if (matches.size() != 1) {
            throw new IdempotencyConflictException();
        }
        return matches.getFirst();
    }

    private static BigDecimal scale(BigDecimal amount) {
        if (amount == null) {
            throw new InvalidTransferException("Transfer amount is required");
        }
        return amount.setScale(Account.MONEY_SCALE, Account.MONEY_ROUNDING);
    }
}
