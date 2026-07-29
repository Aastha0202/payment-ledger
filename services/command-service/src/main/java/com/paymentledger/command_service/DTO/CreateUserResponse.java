package com.paymentledger.command_service.DTO;

import com.paymentledger.command_service.constants.UserStatus;
import com.paymentledger.command_service.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateUserResponse {

    private String userId;
    private String name;
    private String email;
    private String addressLine1;
    private String city;
    private String state;
    private String country;
    private String pincode;
    private UserStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;


    public static CreateUserResponse from(User userDTO) {
        return CreateUserResponse.builder()
                .userId(userDTO.getId().toString())
                .name(userDTO.getName())
                .email(userDTO.getEmail())
                .addressLine1(userDTO.getAddress())
                .city(userDTO.getCity())
                .state(userDTO.getState())
                .country(userDTO.getCountry())
                .pincode(userDTO.getPincode())
                .status(userDTO.getStatus())
                .createdAt(userDTO.getCreatedAt())
                .updatedAt(userDTO.getUpdatedAt())
                .build();
    }

}
