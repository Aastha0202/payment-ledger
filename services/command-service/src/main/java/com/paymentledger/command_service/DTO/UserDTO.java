package com.paymentledger.command_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserDTO {

    private String name;
    private String email;
    private UUID id;
    private String address;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
