package com.paymentledger.projection_service;

public class AccountNotFoundException  extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
