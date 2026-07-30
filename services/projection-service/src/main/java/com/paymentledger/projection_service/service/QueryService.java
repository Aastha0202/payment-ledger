package com.paymentledger.projection_service.service;

import com.paymentledger.projection_service.dto.BalanceResponse;
import com.paymentledger.projection_service.dto.StatementResponse;

import java.util.UUID;

public interface QueryService {


    /**
     * Retrieves the balance for a given account ID.
     *
     * @param accountId the UUID of the account
     * @return a BalanceResponse containing the account's balance information
     */
    BalanceResponse getBalance(UUID accountId);


    /**
     * Retrieves the account statement for a given account ID.
     *
     * @param accountId the UUID of the account
     * @return a StatementResponse containing the account's statement information
     */
    StatementResponse getAccountStatement(UUID accountId);
}
