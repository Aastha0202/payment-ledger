package com.paymentledger.command_service.service.impl;

import com.paymentledger.command_service.DTO.CreateAccountRequest;
import com.paymentledger.command_service.DTO.CreateAccountResponse;
import com.paymentledger.command_service.DTO.TransferRequest;
import com.paymentledger.command_service.DTO.TransferResponse;
import com.paymentledger.command_service.constants.AccountStatus;
import com.paymentledger.command_service.constants.EntryType;
import com.paymentledger.command_service.constants.OutboxStatus;
import com.paymentledger.command_service.constants.TransferSagaStatus;
import com.paymentledger.command_service.constants.UserStatus;
import com.paymentledger.command_service.entity.Account;
import com.paymentledger.command_service.entity.JournalEntry;
import com.paymentledger.command_service.entity.Outbox;
import com.paymentledger.command_service.entity.TransferSaga;
import com.paymentledger.command_service.entity.User;
import com.paymentledger.command_service.exception.AccountNotActiveException;
import com.paymentledger.command_service.exception.AccountNotFoundException;
import com.paymentledger.command_service.exception.DuplicateRequestException;
import com.paymentledger.command_service.exception.InsufficientFundsException;
import com.paymentledger.command_service.exception.JsonProcessingException;
import com.paymentledger.command_service.exception.ServiceUnavailableException;
import com.paymentledger.command_service.exception.UserNotActiveException;
import com.paymentledger.command_service.exception.UserNotFoundException;
import com.paymentledger.command_service.repository.AccountRepository;
import com.paymentledger.command_service.repository.JournalEntryRepository;
import com.paymentledger.command_service.repository.OutboxRepository;
import com.paymentledger.command_service.repository.TransferSagaRepository;
import com.paymentledger.command_service.repository.UserRepository;
import com.paymentledger.command_service.service.IdempotencyService;
import com.paymentledger.command_service.service.TransferService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
public class TransferServiceImpl implements TransferService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private TransferSagaRepository transferSagaRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;

    public TransferServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public CreateAccountResponse createAccount(CreateAccountRequest createAccountRequest) throws UserNotFoundException, UserNotActiveException {
        // Implementation of account creation logic


        if (createAccountRequest.getUserId() == null) {
            throw new IllegalArgumentException("User does not exist");
        }

        // Validate user exists and is active
        User user = userRepository.findById(createAccountRequest.getUserId())
                .orElseThrow(() -> new UserNotFoundException(
                        "User not found: " + createAccountRequest.getUserId()));

        if (!UserStatus.ACTIVE.equals(user.getStatus())) {
            throw new UserNotActiveException(
                    "User is not active: " + createAccountRequest.getUserId());
        }

        // Create account using Builder pattern to ensure defaults are applied
        Account userAccount = Account.builder()
                .userId(createAccountRequest.getUserId())
                .accountType(createAccountRequest.getAccountType())
                .currency(createAccountRequest.getCurrency())
                .build();

        // Use saveAndFlush to ensure timestamps are populated before returning
        Account savedAccount = accountRepository.saveAndFlush(userAccount);
        return CreateAccountResponse.from(savedAccount);
    }

    @Override
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferResponse transferFunds(TransferRequest request) {

        // Step 1: Redis idempotency check — OUTSIDE transaction
        try {
            TransferResponse cached =
                    checkAndStoreIdempotencyKey(request.getIdempotencyKey());
            if (cached != null) {
                return cached;
            }
        } catch (RedisConnectionFailureException e) {
            throw new ServiceUnavailableException(
                    "Payment service temporarily unavailable. Please retry.");
        }

        // Step 2: Execute transfer — INSIDE transaction
        // Validate sender exists and is active
        Account sender = accountRepository
                .findById(request.getSenderAccountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Sender account not found: "
                                + request.getSenderAccountId()));

        if (!AccountStatus.ACTIVE.equals(sender.getStatus())) {
            throw new AccountNotActiveException(
                    "Sender account is not active: "
                            + request.getSenderAccountId());
        }

        // Validate receiver exists and is active
        Account receiver = accountRepository
                .findById(request.getReceiverAccountId())
                .orElseThrow(() -> new AccountNotFoundException(
                        "Receiver account not found: "
                                + request.getReceiverAccountId()));

        if (!AccountStatus.ACTIVE.equals(receiver.getStatus())) {
            throw new AccountNotActiveException(
                    "Receiver account is not active: "
                            + request.getReceiverAccountId());
        }

        // Validate sufficient funds
        if (sender.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient funds. Available: " + sender.getBalance()
                            + ", Required: " + request.getAmount());
        }

        // Update cached balances
        sender.setBalance(
                sender.getBalance().subtract(request.getAmount()));
        receiver.setBalance(
                receiver.getBalance().add(request.getAmount()));
        accountRepository.save(sender);
        accountRepository.save(receiver);

        // Create saga — source of truth for this transfer
        TransferSaga saga = transferSagaRepository.save(
                TransferSaga.builder()
                        .senderId(request.getSenderAccountId())
                        .receiverId(request.getReceiverAccountId())
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .status(TransferSagaStatus.COMPLETED)
                        .description(request.getDescription())
                        .completedAt(LocalDateTime.now())
                        .build());

        // Create immutable journal entries
        journalEntryRepository.save(JournalEntry.builder()
                .accountId(request.getSenderAccountId())
                .transferId(saga.getId())
                .entryType(EntryType.DEBIT)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .referenceType("TRANSFER")
                .referenceId(saga.getId())
                .build());

        journalEntryRepository.save(JournalEntry.builder()
                .accountId(request.getReceiverAccountId())
                .transferId(saga.getId())
                .entryType(EntryType.CREDIT)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description(request.getDescription())
                .referenceType("TRANSFER")
                .referenceId(saga.getId())
                .build());

        // Write outbox event — same transaction guarantees consistency
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "transferId", saga.getId().toString(),
                    "senderId",   saga.getSenderId().toString(),
                    "receiverId", saga.getReceiverId().toString(),
                    "amount",     saga.getAmount().toString(),
                    "currency",   saga.getCurrency()
            ));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(
                    "Failed to serialize outbox payload", e);
        }

        outboxRepository.save(Outbox.builder()
                .aggregateId(saga.getId())
                .eventType("TransferCompleted")
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .build());

        TransferResponse response = TransferResponse.builder()
                .transactionId(saga.getId().toString())
                .amount(saga.getAmount())
                .currency(saga.getCurrency())
                .description(saga.getDescription())
                .status(saga.getStatus().name())
                .completedAt(saga.getCompletedAt())
                .build();

        // Step 3: Extend Redis TTL — OUTSIDE transaction
        try {
            idempotencyService.store(
                    request.getIdempotencyKey(),
                    objectMapper.writeValueAsString(response));
        } catch (Exception e) {
            log.warn("Failed to extend idempotency TTL for key: {}. " +
                    "Transfer succeeded.", request.getIdempotencyKey(), e);
        }

        return response;
    }



    private TransferResponse checkAndStoreIdempotencyKey(
            String idempotencyKey) {
        if (idempotencyService.isDuplicate(idempotencyKey)) {
            String cached = idempotencyService
                    .getCachedResponse(idempotencyKey);
            try {
                return objectMapper.readValue(
                        cached, TransferResponse.class);
            } catch (JsonProcessingException e) {
                log.warn("Could not deserialize cached response " +
                        "for key: {}. Will reprocess.", idempotencyKey, e);
                return null;
            }
        }
        idempotencyService.storeTemporary(idempotencyKey, "PROCESSING");
        return null;
    }

}
