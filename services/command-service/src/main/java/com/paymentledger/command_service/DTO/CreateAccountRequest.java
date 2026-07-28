package com.paymentledger.command_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class CreateAccountRequest {
    private UUID userId;
    private String accountType;
    private String currency;
}
