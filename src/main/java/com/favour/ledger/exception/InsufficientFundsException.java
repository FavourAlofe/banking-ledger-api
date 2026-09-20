package com.favour.ledger.exception;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber) {
        super("Insufficient funds on account " + accountNumber);
    }
}
