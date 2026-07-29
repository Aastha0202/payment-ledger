package com.paymentledger.command_service.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateUserRequest {

    @NotBlank(message = "Name cannot be blank")
    @NotNull(message = "Name cannot be null")
    private String name;

    @NotBlank(message = "Email cannot be blank")
    @NotNull(message = "Email cannot be null")
    private String email;

    private String address;
    private String city;
    private String state;
    private String country;
    private String pincode;

}
