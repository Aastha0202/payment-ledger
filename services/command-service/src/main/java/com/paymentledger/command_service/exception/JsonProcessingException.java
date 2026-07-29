package com.paymentledger.command_service.exception;

public class JsonProcessingException extends RuntimeException {
    public JsonProcessingException(String message) {
        super(message);
    }
}
