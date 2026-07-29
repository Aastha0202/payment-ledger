package com.paymentledger.command_service.controller;

import com.paymentledger.command_service.DTO.ErrorResponse;
import com.paymentledger.command_service.exception.AccountNotActiveException;
import com.paymentledger.command_service.exception.AccountNotFoundException;
import com.paymentledger.command_service.exception.DuplicateEmailException;
import com.paymentledger.command_service.exception.DuplicateRequestException;
import com.paymentledger.command_service.exception.InsufficientFundsException;
import com.paymentledger.command_service.exception.ServiceUnavailableException;
import com.paymentledger.command_service.exception.UserNotActiveException;
import com.paymentledger.command_service.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UserNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND,
                ex.getMessage(), "USER_NOT_FOUND");
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex) {
        return buildError(HttpStatus.NOT_FOUND,
                ex.getMessage(), "ACCOUNT_NOT_FOUND");
    }

    @ExceptionHandler(AccountNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotActive(
            AccountNotActiveException ex) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(), "ACCOUNT_NOT_ACTIVE");
    }

    @ExceptionHandler(UserNotActiveException.class)
    public ResponseEntity<ErrorResponse> handleUserNotActive(
            UserNotActiveException ex) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(), "USER_NOT_ACTIVE");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientFunds(
            InsufficientFundsException ex) {
        return buildError(HttpStatus.UNPROCESSABLE_ENTITY,
                ex.getMessage(), "INSUFFICIENT_FUNDS");
    }

    @ExceptionHandler(DuplicateRequestException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(
            DuplicateRequestException ex) {
        return buildError(HttpStatus.CONFLICT,
                ex.getMessage(), "DUPLICATE_REQUEST");
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(
            ServiceUnavailableException ex) {
        return buildError(HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage(), "SERVICE_UNAVAILABLE");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationError(
            MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        return buildError(HttpStatus.BAD_REQUEST,
                message, "VALIDATION_ERROR");
    }

    @ExceptionHandler(DuplicateEmailException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmailException(
            DuplicateEmailException ex) {
        return buildError(HttpStatus.CONFLICT,
                ex.getMessage(), "DUPLICATE_EMAIL");
    }

    private ResponseEntity<ErrorResponse> buildError(
            HttpStatus status, String message, String errorCode) {
        return ResponseEntity
                .status(status)
                .body(ErrorResponse.builder()
                        .message(message)
                        .errorCode(errorCode)
                        .timestamp(LocalDateTime.now())
                        .build());
    }
}