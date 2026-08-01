package com.paymentledger.command_service.exception;

import com.paymentledger.command_service.constants.TransferSagaStatus;

public class IllegalStateTransitionException
        extends RuntimeException {
    public IllegalStateTransitionException(
            TransferSagaStatus from,
            TransferSagaStatus to) {
        super("Invalid transition: " + from + " → " + to);
    }
}
