package com.favour.ledger.exception;

public class DuplicateAccountException extends RuntimeException {

    public DuplicateAccountException(String accountNumber) {
        super("Account number already exists: " + accountNumber);
    }
}
