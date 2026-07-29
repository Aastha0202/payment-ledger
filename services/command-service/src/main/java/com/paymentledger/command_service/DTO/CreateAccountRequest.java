package com.paymentledger.command_service.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

    @NotNull(message = "User ID cannot be null")
    private UUID userId;

    @NotNull(message = "Account type cannot be null")
    @NotBlank(message = "Account type cannot be blank")
    private String accountType;

    @NotNull(message = "Currency cannot be null")
    @NotBlank(message = "Currency cannot be blank")
    private String currency;
}
