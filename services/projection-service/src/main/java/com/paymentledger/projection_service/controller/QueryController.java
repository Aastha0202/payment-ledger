package com.paymentledger.projection_service.controller;

import com.paymentledger.projection_service.dto.BalanceResponse;
import com.paymentledger.projection_service.dto.StatementResponse;
import com.paymentledger.projection_service.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class QueryController {

    @Autowired
    private QueryService queryService;

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable UUID accountId) {
        BalanceResponse balanceResponse = queryService.getBalance(accountId);
        return ResponseEntity.ok(balanceResponse);
    }

    @GetMapping("/{accountId}/statement")
    public ResponseEntity<StatementResponse> getAccountStatement(@PathVariable UUID accountId) {
        StatementResponse statementResponse = queryService.getAccountStatement(accountId);
        // You can add additional logic here to summarize the balance if needed
        return ResponseEntity.ok(statementResponse);
    }

}
