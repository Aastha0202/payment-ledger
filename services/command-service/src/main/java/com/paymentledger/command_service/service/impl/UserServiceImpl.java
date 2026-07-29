package com.paymentledger.command_service.service.impl;

import com.paymentledger.command_service.DTO.CreateUserRequest;
import com.paymentledger.command_service.DTO.CreateUserResponse;
import com.paymentledger.command_service.entity.User;
import com.paymentledger.command_service.exception.DuplicateEmailException;
import com.paymentledger.command_service.repository.UserRepository;
import com.paymentledger.command_service.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public CreateUserResponse createUser(CreateUserRequest request) {

        // Check if user with the same email already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new DuplicateEmailException("User with email " + request.getEmail() + " already exists.");
        }

        // Create a new user entity using Builder pattern to ensure defaults are applied
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .address(request.getAddress())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry())
                .pincode(request.getPincode())
                .build();

        // Use saveAndFlush to ensure timestamps are populated before returning
        User savedUser = userRepository.saveAndFlush(user);

        // Create a response object and return it
        return CreateUserResponse.from(savedUser);

    }
}
