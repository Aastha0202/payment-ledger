package com.paymentledger.command_service.controller;

import com.paymentledger.command_service.DTO.CreateUserRequest;
import com.paymentledger.command_service.DTO.CreateUserResponse;
import com.paymentledger.command_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/v1")
public class UserController {


    @Autowired
    private UserService userService;

    @PostMapping("/users")
    public ResponseEntity<CreateUserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserResponse response = userService.createUser(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getUserId())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

}
