package com.paymentledger.command_service.service;

import org.springframework.stereotype.Service;


public interface IdempotencyService {

    /**
     * Checks if the given idempotency key has already been used.
     *
     * @param idempotencyKey the idempotency key to check
     * @return true if the key has been used, false otherwise
     */
    boolean isDuplicate(String idempotencyKey);

    /**
     * Stores the response associated with the given idempotency key.
     *
     * @param idempotencyKey the idempotency key to store
     * @param response the response to store
     */
    void store(String idempotencyKey, String response);


}
