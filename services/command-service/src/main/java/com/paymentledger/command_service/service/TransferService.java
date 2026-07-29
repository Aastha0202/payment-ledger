package com.paymentledger.command_service.service;

import com.paymentledger.command_service.DTO.CreateAccountRequest;
import com.paymentledger.command_service.DTO.CreateAccountResponse;
import com.paymentledger.command_service.DTO.TransferRequest;
import com.paymentledger.command_service.DTO.TransferResponse;
import com.paymentledger.command_service.exception.DuplicateRequestException;
import com.paymentledger.command_service.exception.InsufficientFundsException;
import com.paymentledger.command_service.exception.UserNotActiveException;
import com.paymentledger.command_service.exception.UserNotFoundException;

import javax.naming.ServiceUnavailableException;

public interface TransferService {


    /**
     * Creates a new account for a user.
     *
     * @param createAccountRequest the request containing user ID, account type, and currency
     * @return the response containing the created account details
     */
    CreateAccountResponse createAccount(CreateAccountRequest createAccountRequest) throws UserNotFoundException, UserNotActiveException;


    /**
     * Transfers funds from one account to another.
     *
     * @param transferRequest the request containing sender account ID, receiver account ID, amount, currency, description, and idempotency key
     * @return the response containing the transfer details
     * @throws UserNotFoundException if either the sender or receiver user is not found
     * @throws UserNotActiveException if either the sender or receiver user is not active
     * @throws InsufficientFundsException if the sender has insufficient funds for the transfer
     */
    TransferResponse transferFunds(TransferRequest transferRequest) throws UserNotFoundException, UserNotActiveException, InsufficientFundsException, DuplicateRequestException;

}
