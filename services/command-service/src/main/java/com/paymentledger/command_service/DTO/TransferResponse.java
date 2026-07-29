package com.paymentledger.command_service.DTO;

import com.paymentledger.command_service.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransferResponse {

    private String transactionId;
    private BigDecimal amount;
    private String currency;
    private String description;
    private String status;
    private LocalDateTime completedAt;

}
