package com.paymentledger.command_service.service;

import com.paymentledger.command_service.DTO.CreateUserRequest;
import com.paymentledger.command_service.DTO.CreateUserResponse;

public interface UserService {

    /**
     * Creates a new user based on the provided request.
     *
     * @param request The request containing user details.
     * @return A response indicating the result of the user creation.
     */
    CreateUserResponse createUser(CreateUserRequest request);
}
