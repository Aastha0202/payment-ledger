package com.paymentledger.command_service.controller;

import com.paymentledger.command_service.DTO.CreateAccountRequest;
import com.paymentledger.command_service.DTO.CreateAccountResponse;
import com.paymentledger.command_service.service.TransferService;
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
@RequestMapping("/api/v1/accounts")
public class AccountController {

    @Autowired
    private TransferService transferService;

    @PostMapping()
    public ResponseEntity<CreateAccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest accountRequest) {
        CreateAccountResponse response = transferService.createAccount(accountRequest);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(response);
    }

}
