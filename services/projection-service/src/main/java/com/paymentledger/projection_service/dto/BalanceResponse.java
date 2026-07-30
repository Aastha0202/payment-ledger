package com.paymentledger.projection_service.dto;

import com.paymentledger.projection_service.entity.Account;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BalanceResponse {

    private UUID accountId;
    private BigDecimal balance;
    private String currency;
    private LocalDateTime lastUpdated;


    public static BalanceResponse from(Account account) {
        return BalanceResponse.builder()
                .accountId(account.getId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .lastUpdated(account.getUpdatedAt())
                .build();
    }

}
