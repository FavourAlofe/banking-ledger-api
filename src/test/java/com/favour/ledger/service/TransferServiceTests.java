package com.favour.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.favour.ledger.PostgresTestConfiguration;
import com.favour.ledger.exception.IdempotencyConflictException;
import com.favour.ledger.exception.InsufficientFundsException;
import com.favour.ledger.exception.InvalidTransferException;
import com.favour.ledger.model.Account;
import com.favour.ledger.model.LedgerTransaction;
import com.favour.ledger.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class TransferServiceTests {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    void postsBalancedTransferAndUpdatesBalances() {
        Account source = open("ACC-T-SRC", "100.00");
        Account destination = open("ACC-T-DST", "10.00");

        LedgerTransaction posted = transferService.transfer(
                UUID.randomUUID(),
                source.getAccountNumber(),
                destination.getAccountNumber(),
                new BigDecimal("25.50"),
                "rent"
        );

        assertThat(posted.getEntries()).hasSize(2);
        assertThat(reload(source).getBalance()).isEqualByComparingTo("74.50");
        assertThat(reload(destination).getBalance()).isEqualByComparingTo("35.50");
    }

    @Test
    void replaysIdenticalIdempotentRequest() {
        Account source = open("ACC-T-IDEM-S", "50.00");
        Account destination = open("ACC-T-IDEM-D", "0.00");
        UUID key = UUID.randomUUID();

        LedgerTransaction first = transferService.transfer(
                key, source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("10.00"), "once"
        );
        LedgerTransaction second = transferService.transfer(
                key, source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("10.00"), "once"
        );

        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(reload(source).getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    void rejectsIdempotencyKeyReuseWithDifferentPayload() {
        Account source = open("ACC-T-CONF-S", "50.00");
        Account destination = open("ACC-T-CONF-D", "0.00");
        UUID key = UUID.randomUUID();
        transferService.transfer(
                key, source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("10.00"), "first"
        );

        assertThatThrownBy(() -> transferService.transfer(
                key, source.getAccountNumber(), destination.getAccountNumber(), new BigDecimal("11.00"), "second"
        )).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void rejectsOverdraft() {
        Account source = open("ACC-T-OD-S", "5.00");
        Account destination = open("ACC-T-OD-D", "0.00");

        assertThatThrownBy(() -> transferService.transfer(
                UUID.randomUUID(),
                source.getAccountNumber(),
                destination.getAccountNumber(),
                new BigDecimal("5.01"),
                "overdraft"
        )).isInstanceOf(InsufficientFundsException.class);
        assertThat(reload(source).getBalance()).isEqualByComparingTo("5.00");
    }

    @Test
    void rejectsSelfTransfer() {
        Account account = open("ACC-T-SELF", "20.00");
        assertThatThrownBy(() -> transferService.transfer(
                UUID.randomUUID(),
                account.getAccountNumber(),
                account.getAccountNumber(),
                new BigDecimal("1.00"),
                "self"
        )).isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void concurrentDebitsDoNotOverdraft() throws Exception {
        Account source = open("ACC-T-CON-S", "100.00");
        Account destination = open("ACC-T-CON-D", "0.00");
        int attempts = 20;
        BigDecimal amount = new BigDecimal("10.00");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger shortages = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        try {
            for (int i = 0; i < attempts; i++) {
                pool.submit(() -> {
                    start.await();
                    try {
                        transferService.transfer(
                                UUID.randomUUID(),
                                source.getAccountNumber(),
                                destination.getAccountNumber(),
                                amount,
                                "race"
                        );
                        successes.incrementAndGet();
                    } catch (InsufficientFundsException ex) {
                        shortages.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                    return null;
                });
            }
            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(10);
        assertThat(shortages.get()).isEqualTo(10);
        assertThat(reload(source).getBalance()).isEqualByComparingTo("0.00");
        assertThat(reload(destination).getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void bidirectionalTransfersDoNotDeadlock() throws Exception {
        Account left = open("ACC-T-DL-A", "100.00");
        Account right = open("ACC-T-DL-B", "100.00");
        int pairs = 12;
        ExecutorService pool = Executors.newFixedThreadPool(pairs * 2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < pairs; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    transferService.transfer(
                            UUID.randomUUID(),
                            left.getAccountNumber(),
                            right.getAccountNumber(),
                            new BigDecimal("1.00"),
                            "a-to-b"
                    );
                    return null;
                }));
                futures.add(pool.submit(() -> {
                    start.await();
                    transferService.transfer(
                            UUID.randomUUID(),
                            right.getAccountNumber(),
                            left.getAccountNumber(),
                            new BigDecimal("1.00"),
                            "b-to-a"
                    );
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(reload(left).getBalance()).isEqualByComparingTo("100.00");
        assertThat(reload(right).getBalance()).isEqualByComparingTo("100.00");
    }

    private Account open(String suffix, String opening) {
        String number = suffix + "-" + UUID.randomUUID().toString().substring(0, 8);
        return accountService.create(number, "Jane Doe", new BigDecimal(opening));
    }

    private Account reload(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow();
    }
}
