package com.paymentledger.command_service.service.impl;

import com.paymentledger.command_service.DTO.CreateAccountRequest;
import com.paymentledger.command_service.DTO.CreateAccountResponse;
import com.paymentledger.command_service.constants.UserStatus;
import com.paymentledger.command_service.entity.Account;
import com.paymentledger.command_service.entity.User;
import com.paymentledger.command_service.exception.UserNotActiveException;
import com.paymentledger.command_service.exception.UserNotFoundException;
import com.paymentledger.command_service.repository.AccountRepository;
import com.paymentledger.command_service.repository.UserRepository;
import com.paymentledger.command_service.service.TransferService;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class TransferServiceImpl implements TransferService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;


    @Override
    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest createAccountRequest) {
        // Implementation of account creation logic


        if (createAccountRequest.getUserId() == null) {
            throw new IllegalArgumentException("User does not exist");
        }

        // Validate user exists and is active
        User user = userRepository.findById(createAccountRequest.getUserId())
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + createAccountRequest.getUserId()));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new UserNotActiveException(
                    "User is not active: " + createAccountRequest.getUserId());
        }

        // Here you would implement the logic to create the account and return the created AccountDTO

        Account userAccount = new Account();
        userAccount.setUserId(createAccountRequest.getUserId());
        userAccount.setCurrency(createAccountRequest.getCurrency());
        userAccount.setAccountType(createAccountRequest.getAccountType());
        userAccount.setBalance(BigDecimal.ZERO); // Assuming BIG_DECIMAL_ZERO is defined elsewhere

         userAccount = accountRepository.save(userAccount);


         return CreateAccountResponse.from(userAccount);
    }
}
