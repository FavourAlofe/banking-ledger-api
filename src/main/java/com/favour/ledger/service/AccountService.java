package com.favour.ledger.service;

import com.favour.ledger.exception.AccountNotFoundException;
import com.favour.ledger.exception.DuplicateAccountException;
import com.favour.ledger.exception.InsufficientFundsException;
import com.favour.ledger.exception.InvalidTransferException;
import com.favour.ledger.model.Account;
import com.favour.ledger.model.AccountStatus;
import com.favour.ledger.model.LedgerTransaction;
import com.favour.ledger.repository.AccountRepository;
import com.favour.ledger.repository.LedgerTransactionRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final LedgerTransactionRepository ledgerTransactionRepository;

    public AccountService(
            AccountRepository accountRepository,
            LedgerTransactionRepository ledgerTransactionRepository
    ) {
        this.accountRepository = accountRepository;
        this.ledgerTransactionRepository = ledgerTransactionRepository;
    }

    @Transactional
    public Account create(String accountNumber, String ownerName, BigDecimal openingBalance) {
        if (accountRepository.existsByAccountNumber(accountNumber)) {
            throw new DuplicateAccountException(accountNumber);
        }
        return accountRepository.save(new Account(accountNumber, ownerName, openingBalance));
    }

    @Transactional(readOnly = true)
    public Account getByAccountNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Account> list() {
        return accountRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<LedgerTransaction> history(String accountNumber) {
        if (!accountRepository.existsByAccountNumber(accountNumber)) {
            throw new AccountNotFoundException(accountNumber);
        }
        return ledgerTransactionRepository.findDistinctByEntries_Account_AccountNumberOrderByCreatedAtDesc(
                accountNumber
        );
    }

    static void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidTransferException("Account is not active: " + account.getAccountNumber());
        }
    }

    static void requireFunds(Account account, BigDecimal amount) {
        if (!account.canDebit(amount)) {
            throw new InsufficientFundsException(account.getAccountNumber());
        }
    }
}
