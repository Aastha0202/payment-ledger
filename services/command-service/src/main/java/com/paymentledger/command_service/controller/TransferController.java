package com.paymentledger.command_service.controller;

import com.paymentledger.command_service.DTO.TransferRequest;
import com.paymentledger.command_service.DTO.TransferResponse;
import com.paymentledger.command_service.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    @Autowired
    private TransferService transferService;


    @PostMapping()
    public ResponseEntity<TransferResponse> transferFunds(
            @RequestHeader(value = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody TransferRequest transferRequest) {
        //set the idempotency key in the request object
        transferRequest.setIdempotencyKey(idempotencyKey);
        TransferResponse response = transferService.transferFunds(transferRequest);
        return ResponseEntity.ok(response);
    }
}
