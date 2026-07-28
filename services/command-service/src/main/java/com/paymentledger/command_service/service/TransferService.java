package com.paymentledger.command_service.service;

import com.paymentledger.command_service.DTO.CreateAccountRequest;
import com.paymentledger.command_service.DTO.CreateAccountResponse;

public interface TransferService {


    CreateAccountResponse createAccount(CreateAccountRequest createAccountRequest);

}
