package com.paymentledger.projection_service.service.impl;

import com.paymentledger.projection_service.AccountNotFoundException;
import com.paymentledger.projection_service.dto.BalanceResponse;
import com.paymentledger.projection_service.dto.JournalEntryDTO;
import com.paymentledger.projection_service.dto.StatementResponse;
import com.paymentledger.projection_service.entity.Account;
import com.paymentledger.projection_service.entity.JournalEntry;
import com.paymentledger.projection_service.repository.AccountRepository;
import com.paymentledger.projection_service.repository.JournalEntryRepository;
import com.paymentledger.projection_service.service.QueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class QueryServiceImpl implements QueryService {

    @Autowired
    private AccountRepository accountRepository; // Assuming you have an AccountRepository to fetch account data

    @Autowired
    private JournalEntryRepository journalEntryRepository; // Assuming you have a JournalEntryRepository to fetch

    @Override
    public BalanceResponse getBalance(java.util.UUID accountId) {
        // Implementation logic to retrieve balance for the given accountId
        // You can use the accountRepository to fetch the account data and calculate the balance
        // For example:
         Account account = accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException("Account not found for ID: " + accountId));

        return BalanceResponse.from(account);
    }

    @Override
    public StatementResponse getAccountStatement(UUID accountId) {
        // Implementation logic to retrieve account statement for the given accountId
        // You can use the accountRepository to fetch the account data and generate the statement
        // For example:
        Account account = accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException("Account not found for ID: " + accountId));

        List<JournalEntry> latestEntries = journalEntryRepository.findByAccountIdOrderByCreatedAtDesc(accountId);

        List<JournalEntryDTO> journalEntryDTOs = latestEntries.stream()
                .map(JournalEntryDTO::from)
                .toList();


        return  StatementResponse.from(accountId, journalEntryDTOs);
    }
}
