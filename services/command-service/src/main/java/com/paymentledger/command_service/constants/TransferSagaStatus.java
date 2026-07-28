package com.paymentledger.command_service.constants;

public enum TransferSagaStatus {
    INITIATED,
    DEBIT_PENDING,
    DEBIT_DONE,
    CREDIT_PENDING,
    COMPLETED,
    COMPENSATING,
    FAILED
}