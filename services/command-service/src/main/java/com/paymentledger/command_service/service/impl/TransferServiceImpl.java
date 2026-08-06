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
import com.paymentledger.command_service.exception.InsufficientFundsException;
import com.paymentledger.command_service.exception.ServiceUnavailableException;
import com.paymentledger.command_service.exception.TransferFailedException;
import com.paymentledger.command_service.exception.UserNotActiveException;
import com.paymentledger.command_service.exception.UserNotFoundException;
import com.paymentledger.command_service.metrics.TransferMetrics;
import com.paymentledger.command_service.repository.AccountRepository;
import com.paymentledger.command_service.repository.JournalEntryRepository;
import com.paymentledger.command_service.repository.OutboxRepository;
import com.paymentledger.command_service.repository.TransferSagaRepository;
import com.paymentledger.command_service.repository.UserRepository;
import com.paymentledger.command_service.saga.TransferSagaStateMachine;
import com.paymentledger.command_service.service.IdempotencyService;
import com.paymentledger.command_service.service.TransferService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

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
    private OutboxRepository outboxRepository;

    private final ObjectMapper objectMapper;

    @Autowired
    private TransferSagaStateMachine transferSagaStateMachine;

    @Autowired
   private TransferSagaRepository sagaRepository;

    @Lazy
    @Autowired
    private TransferServiceImpl self;

    @Autowired
    private TransferMetrics transferMetrics;

    public TransferServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
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
    public TransferResponse transferFunds(TransferRequest request) {

        // Step 1: Redis idempotency check — OUTSIDE transaction
        TransferSaga saga;
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

        // At the start of transferFunds:
        long startTime = System.currentTimeMillis();
        saga = self.createTransferSaga(request);

        try {
            //create a new transfer saga with status INITIATED
            saga = self.executeDebit(saga, sender, request);
        }catch (Exception e) {
            self.markSagaFailed(saga, "Debit failed: " + e.getMessage());
            // On compensation:
            transferMetrics.recordCompensation();
            transferMetrics.recordTransferFailure("credit_failed");
            throw new TransferFailedException(
                    "Failed to create transfer saga");

        }

        try {
           saga =  self.executeCredit(saga, receiver, request);
        } catch (Exception e) {
          self.compensateCredit(saga, sender, request);
            // On compensation:
            transferMetrics.recordCompensation();
            transferMetrics.recordTransferFailure("credit_failed");
            throw new TransferFailedException(
                    "Failed to execute credit operation");
        }

        // On success:
        transferMetrics.recordTransferSuccess(request.getCurrency());
        transferMetrics.recordTransferDuration(
                System.currentTimeMillis() - startTime);

        boolean outboxPublished = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                self.publishOutBoxEvent(saga);
                outboxPublished = true;
                break;
            } catch (Exception e) {
                log.warn("Outbox publish attempt {}/3 failed " +
                                "for saga {}: {}",
                        attempt, saga.getId(), e.getMessage());
            }
        }

        if (!outboxPublished) {
            log.error("CRITICAL: Saga {} financially COMPLETED " +
                    "but outbox event failed after 3 attempts. " +
                    "Manual review required.", saga.getId());
        }

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
            } catch (JacksonException e) {
                log.warn("Could not deserialize cached response " +
                        "for key: {}. Will reprocess.", idempotencyKey, e);
                return null;
            }
        }
        idempotencyService.storeTemporary(idempotencyKey, "PROCESSING");
        return null;
    }


    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferSaga createTransferSaga(TransferRequest request) {
        TransferSaga saga = TransferSaga.builder()
                .senderId(request.getSenderAccountId())
                .receiverId(request.getReceiverAccountId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(TransferSagaStatus.INITIATED)
                .description(request.getDescription())
                .build();

       saga= sagaRepository.save(saga);
        return saga;

    }


    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferSaga executeDebit(TransferSaga saga, Account sender, TransferRequest request) {
        // Deduct amount from sender's account
       saga= transferSagaStateMachine.transition(saga, TransferSagaStatus.DEBIT_PENDING);
        sender.setBalance(sender.getBalance().subtract(request.getAmount()));
        accountRepository.save(sender);
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
       saga= transferSagaStateMachine.transition(saga, TransferSagaStatus.DEBIT_DONE);
        return saga;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public TransferSaga executeCredit(TransferSaga saga, Account receiver, TransferRequest request) {
        saga = transferSagaStateMachine.transition(saga, TransferSagaStatus.CREDIT_PENDING);
        // Add amount to receiver's account
        receiver.setBalance(receiver.getBalance().add(request.getAmount()));
        accountRepository.save(receiver);
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
        saga = transferSagaStateMachine.transition(saga, TransferSagaStatus.COMPLETED);

        return saga;
    }


    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void markSagaFailed(TransferSaga saga, String reason) {
        saga.setFailureReason(reason);
       saga =  transferSagaStateMachine.transition(saga, TransferSagaStatus.FAILED);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void compensateCredit(TransferSaga saga, Account sender, TransferRequest request) {
        // Deduct amount from receiver's account
        saga = transferSagaStateMachine.transition(saga, TransferSagaStatus.COMPENSATING);
        sender.setBalance(sender.getBalance().add(request.getAmount()));
        accountRepository.save(sender);
        // Create immutable journal entries for compensation
        journalEntryRepository.save(JournalEntry.builder()
                .accountId(request.getSenderAccountId())
                .transferId(saga.getId())
                .entryType(EntryType.COMPENSATION)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .description("Compensation: credit failed, returning funds to sender")
                .referenceType("COMPENSATION")
                .referenceId(saga.getId())
                .build());
        saga.setFailureReason("Credit step failed — compensation applied");
        transferSagaStateMachine.transition(saga, TransferSagaStatus.FAILED);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void publishOutBoxEvent(TransferSaga saga) {
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
        }catch(JacksonException e) {
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
    }
}
