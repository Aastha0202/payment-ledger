package com.paymentledger.command_service.service;

import com.paymentledger.command_service.DTO.CreateAccountRequest;
import com.paymentledger.command_service.DTO.CreateAccountResponse;
import com.paymentledger.command_service.DTO.TransferRequest;
import com.paymentledger.command_service.DTO.TransferResponse;
import com.paymentledger.command_service.entity.Account;
import com.paymentledger.command_service.entity.TransferSaga;
import com.paymentledger.command_service.exception.DuplicateRequestException;
import com.paymentledger.command_service.exception.InsufficientFundsException;
import com.paymentledger.command_service.exception.UserNotActiveException;
import com.paymentledger.command_service.exception.UserNotFoundException;

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

//    /**
//     * Creates a new transfer saga for a transfer request.
//     *
//     * @param request the transfer request containing sender account ID, receiver account ID, amount, currency, description, and idempotency key
//     * @return the created transfer saga
//     */
//    TransferSaga createTransferSaga(TransferRequest request);
//
//    /**
//     * Executes the debit operation for a transfer saga.
//     *
//     * @param saga    the transfer saga to execute the debit for
//     * @param sender  the sender account
//     * @param request the transfer request containing sender account ID, receiver account ID, amount, currency, description, and idempotency key
//     * @return
//     */
//     TransferSaga executeDebit(TransferSaga saga, Account sender, TransferRequest request);
//
//    /**
//     * Executes the credit operation for a transfer saga.
//     *
//     * @param saga     the transfer saga to execute the credit for
//     * @param receiver the receiver account
//     * @param request  the transfer request containing sender account ID, receiver account ID, amount, currency, description, and idempotency key
//     * @return
//     */
//
//     TransferSaga executeCredit(TransferSaga saga, Account receiver, TransferRequest request);
//
//    /**
//     * Compensates the debit operation for a transfer saga in case of failure.
//     *
//     * @param saga the transfer saga to compensate the debit for
//     */
//    void markSagaFailed(TransferSaga saga, String reason);
//
//    /**
//     * compensates the credit operation for a transfer saga in case of failure.
//     *
//     * @param saga     the transfer saga to compensate the credit for
//     * @param receiver the receiver account
//     * @param request  the transfer request containing sender account ID, receiver account ID, amount, currency, description, and idempotency key
//     */
//
//     void compensateCredit(TransferSaga saga, Account receiver, TransferRequest request);
//
//    /**
//     * Publishes an outbox event for a transfer saga.
//     * @param saga
//     */
//     void publishOutBoxEvent(TransferSaga saga);

}
