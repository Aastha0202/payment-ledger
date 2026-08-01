package com.paymentledger.command_service;

import com.paymentledger.command_service.DTO.ErrorResponse;
import com.paymentledger.command_service.DTO.TransferRequest;
import com.paymentledger.command_service.DTO.TransferResponse;
import com.paymentledger.command_service.config.TestKafkaConfig;
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
import com.paymentledger.command_service.repository.AccountRepository;
import com.paymentledger.command_service.repository.JournalEntryRepository;
import com.paymentledger.command_service.repository.OutboxRepository;
import com.paymentledger.command_service.repository.TransferSagaRepository;
import com.paymentledger.command_service.repository.UserRepository;
import com.paymentledger.command_service.service.impl.TransferServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@Import(TestKafkaConfig.class)
class TransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("payment_ledger_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(
            DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                postgres::getJdbcUrl);
        registry.add("spring.datasource.username",
                postgres::getUsername);
        registry.add("spring.datasource.password",
                postgres::getPassword);
        registry.add("spring.data.redis.host",
                () -> "localhost");
    }

    @LocalServerPort
    private int port;

    private RestTemplate restTemplate;

    {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(org.springframework.http.client.ClientHttpResponse response) throws java.io.IOException {
                return false;
            }
        });
    }

    @Autowired
    UserRepository userRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    JournalEntryRepository journalEntryRepository;
    @Autowired
    OutboxRepository outboxRepository;
    @Autowired
    TransferSagaRepository transferSagaRepository;

    @Autowired
    TransferServiceImpl transferService;

    private UUID senderAccountId;
    private UUID receiverAccountId;

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @BeforeEach
    void setUp() {
        // Create sender
        User sender = userRepository.save(User.builder()
                .name("Aastha Modi")
                .email("aastha@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        Account senderAccount = accountRepository.save(
                Account.builder()
                        .userId(sender.getId())
                        .accountType("SAVINGS")
                        .balance(new BigDecimal("10000.00"))
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());
        senderAccountId = senderAccount.getId();

        // Create receiver
        User receiver = userRepository.save(User.builder()
                .name("Priya Sharma")
                .email("priya@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        Account receiverAccount = accountRepository.save(
                Account.builder()
                        .userId(receiver.getId())
                        .accountType("SAVINGS")
                        .balance(new BigDecimal("5000.00"))
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());
        receiverAccountId = receiverAccount.getId();
    }

    @Test
    void transferFunds_shouldDebitSenderAndCreditReceiver() {
        // Build request
        TransferRequest request = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("3000.00"))
                .currency("INR")
                .description("Test transfer")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        // Execute
        ResponseEntity<TransferResponse> response = restTemplate.exchange(
                baseUrl() + "/api/v1/transfers",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                TransferResponse.class);

        // Assert response
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getStatus())
                .isEqualTo("COMPLETED");

        // Assert sender balance decreased
        Account sender = accountRepository
                .findById(senderAccountId).orElseThrow();
        assertThat(sender.getBalance())
                .isEqualByComparingTo(new BigDecimal("7000.0000"));

        // Assert receiver balance increased
        Account receiver = accountRepository
                .findById(receiverAccountId).orElseThrow();
        assertThat(receiver.getBalance())
                .isEqualByComparingTo(new BigDecimal("8000.0000"));

        // Assert journal entries
        UUID transferId = UUID.fromString(
                response.getBody().getTransactionId());
        List<JournalEntry> entries = journalEntryRepository
                .findByTransferId(transferId);
        assertThat(entries).hasSize(2);
        assertThat(entries)
                .extracting(JournalEntry::getEntryType)
                .containsExactlyInAnyOrder(
                        EntryType.DEBIT, EntryType.CREDIT);

        // Assert outbox row
        List<Outbox> outboxRows = outboxRepository
                .findByStatusOrderByCreatedAtAsc(
                        OutboxStatus.PENDING,
                        PageRequest.of(0, 10))
                .getContent();
        assertThat(outboxRows).hasSize(1);
        assertThat(outboxRows.getFirst().getEventType())
                .isEqualTo("TransferCompleted");
    }

    @Test
    void transferFunds_shouldReturn422_whenInsufficientFunds() {
        TransferRequest request = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("99999.00"))
                .currency("INR")
                .description("Should fail")
                .build();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<Map> response = restTemplate.exchange(
                baseUrl() + "/api/v1/transfers",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                Map.class);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("errorCode"))
                .isEqualTo("INSUFFICIENT_FUNDS");

        // Assert no journal entries created
        assertThat(journalEntryRepository.findAll()).isEmpty();

        // Assert no outbox rows created
        assertThat(outboxRepository.findAll()).isEmpty();
    }

    @Test
    void transferFunds_shouldReturn200_whenDuplicateKey() {
        TransferRequest request = TransferRequest.builder()
                .senderAccountId(senderAccountId)
                .receiverAccountId(receiverAccountId)
                .amount(new BigDecimal("1000.00"))
                .currency("INR")
                .description("Duplicate test")
                .build();

        String idempotencyKey = UUID.randomUUID().toString();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", idempotencyKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransferRequest> entity =
                new HttpEntity<>(request, headers);

        // First request
        ResponseEntity<TransferResponse> first = restTemplate.exchange(
                baseUrl() + "/api/v1/transfers", HttpMethod.POST,
                entity, TransferResponse.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Second request — same key
        ResponseEntity<TransferResponse> second = restTemplate.exchange(
                baseUrl() + "/api/v1/transfers", HttpMethod.POST,
                entity, TransferResponse.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Same transaction ID returned both times
        assertThat(first.getBody()).isNotNull();
        assertThat(second.getBody()).isNotNull();
        assertThat(first.getBody().getTransactionId())
                .isEqualTo(second.getBody().getTransactionId());

        // Only ONE saga in the database
        assertThat(transferSagaRepository.findAll()).hasSize(1);

        // Only TWO journal entries (one transfer, not two)
        assertThat(journalEntryRepository.findAll()).hasSize(2);
    }


    @Test
    void compensateCredit_shouldReturnFundsToSender() {
        // Setup
        BigDecimal initialBalance = new BigDecimal("10000.00");
        BigDecimal transferAmount = new BigDecimal("3000.00");

        // Create sender with known balance
        User senderUser = userRepository.save(User.builder()
                .name("Sender User")
                .email("sender.comp@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        Account senderAccount = accountRepository.save(
                Account.builder()
                        .userId(senderUser.getId())
                        .accountType("SAVINGS")
                        .balance(initialBalance)
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());

        // Simulate: saga was created and debit committed
        // Sender balance already reduced (debit happened)
        senderAccount.setBalance(
                initialBalance.subtract(transferAmount));
        accountRepository.save(senderAccount);

        TransferSaga saga = transferSagaRepository.save(
                TransferSaga.builder()
                        .senderId(senderAccount.getId())
                        .receiverId(UUID.randomUUID())
                        .amount(transferAmount)
                        .currency("INR")
                        .status(TransferSagaStatus.CREDIT_PENDING)
                        .build());

        TransferRequest request = TransferRequest.builder()
                .senderAccountId(senderAccount.getId())
                .receiverAccountId(UUID.randomUUID())
                .amount(transferAmount)
                .currency("INR")
                .description("Compensation test")
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        // Execute compensation
        transferService.compensateCredit(saga, senderAccount, request);

        // Assert sender got money back
        Account updatedSender = accountRepository
                .findById(senderAccount.getId()).orElseThrow();
        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(initialBalance);

        // Assert saga is FAILED
        TransferSaga updatedSaga = transferSagaRepository
                .findById(saga.getId()).orElseThrow();
        assertThat(updatedSaga.getStatus())
                .isEqualTo(TransferSagaStatus.FAILED);
        assertThat(updatedSaga.getFailureReason())
                .contains("Credit step failed");

        // Assert compensation journal entry exists
        List<JournalEntry> entries = journalEntryRepository
                .findByTransferId(saga.getId());
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getEntryType())
                .isEqualTo(EntryType.COMPENSATION);
        assertThat(entries.get(0).getAccountId())
                .isEqualTo(senderAccount.getId());
    }

    @Test
    void transferFunds_insufficientFunds_shouldNotCreateSagaOrJournalEntries() {
        User user1 = userRepository.save(User.builder()
                .name("Poor Sender")
                .email("poor@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        Account poorSender = accountRepository.save(
                Account.builder()
                        .userId(user1.getId())
                        .accountType("SAVINGS")
                        .balance(new BigDecimal("100.00"))
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());

        User user2 = userRepository.save(User.builder()
                .name("Receiver")
                .email("receiver.ins@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        Account receiver = accountRepository.save(
                Account.builder()
                        .userId(user2.getId())
                        .accountType("SAVINGS")
                        .balance(new BigDecimal("5000.00"))
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", UUID.randomUUID().toString());
        headers.setContentType(MediaType.APPLICATION_JSON);

        TransferRequest request = TransferRequest.builder()
                .senderAccountId(poorSender.getId())
                .receiverAccountId(receiver.getId())
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .description("Should fail")
                .build();

        ResponseEntity<ErrorResponse> response = restTemplate.exchange(
                "/api/v1/transfers",
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                ErrorResponse.class);

        // Assert correct error response
        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody().getErrorCode())
                .isEqualTo("INSUFFICIENT_FUNDS");

        // Assert sender balance unchanged
        Account updatedSender = accountRepository
                .findById(poorSender.getId()).orElseThrow();
        assertThat(updatedSender.getBalance())
                .isEqualByComparingTo(new BigDecimal("100.00"));

        // Assert NO saga created
        assertThat(transferSagaRepository.findAll()).isEmpty();

        // Assert NO journal entries
        assertThat(journalEntryRepository.findAll()).isEmpty();

        // Assert NO outbox rows
        assertThat(outboxRepository.findAll()).isEmpty();
    }

}
