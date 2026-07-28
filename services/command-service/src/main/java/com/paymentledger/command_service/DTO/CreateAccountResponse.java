package com.paymentledger.command_service.DTO;


import com.paymentledger.command_service.constants.AccountStatus;
import com.paymentledger.command_service.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateAccountResponse {
    private UUID id;
    private UUID userId;
    private String accountType;
    private BigDecimal balance;
    private String currency;
    private AccountStatus status;
    private LocalDateTime createdAt;

    public static CreateAccountResponse from(Account account) {
        return CreateAccountResponse.builder()
                .id(account.getId())
                .userId(account.getUserId())
                .accountType(account.getAccountType())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .status(account.getStatus())
                .createdAt(account.getCreatedAt())
                .build();
    }
}
