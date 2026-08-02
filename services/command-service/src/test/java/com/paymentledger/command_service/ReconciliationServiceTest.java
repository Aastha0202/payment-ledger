package com.paymentledger.command_service;

import com.paymentledger.command_service.config.TestKafkaConfig;
import com.paymentledger.command_service.constants.AccountStatus;
import com.paymentledger.command_service.constants.EntryType;
import com.paymentledger.command_service.constants.UserStatus;
import com.paymentledger.command_service.entity.Account;
import com.paymentledger.command_service.entity.JournalEntry;
import com.paymentledger.command_service.entity.ReconciliationFailure;
import com.paymentledger.command_service.entity.TransferSaga;
import com.paymentledger.command_service.entity.User;
import com.paymentledger.command_service.repository.AccountRepository;
import com.paymentledger.command_service.repository.JournalEntryRepository;
import com.paymentledger.command_service.repository.ReconciliationFailureRepository;
import com.paymentledger.command_service.repository.TransferSagaRepository;
import com.paymentledger.command_service.repository.UserRepository;
import com.paymentledger.command_service.service.ReconciliationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment =
        SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.main.allow-bean-definition-overriding=true")
@Testcontainers
public class ReconciliationServiceTest {


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

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private JournalEntryRepository journalEntryRepository;

    @Autowired
    private ReconciliationFailureRepository reconciliationFailureRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferSagaRepository transferSagaRepository;

    @Test
    void reconciliation_shouldDetectAndFreezeAccountWithMismatch() {

        User user = userRepository.save(User.builder()
                .name("Aastha Modi")
                .email("aastha@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        User user2 = userRepository.save(User.builder()
                .name("John Doe")
                .email("john.doe@test.com")
                .status(UserStatus.ACTIVE)
                .build());

        // Create account with cached balance of 10000
        Account account = accountRepository.save(
                Account.builder()
                        .userId(user.getId())
                        .accountType("SAVINGS")
                        .balance(new BigDecimal("10000.00"))
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());

        Account destinationAccount = accountRepository.save(
                Account.builder()
                        .userId(user2.getId())
                        .accountType("SAVINGS")
                        .balance(new BigDecimal("20000.00"))
                        .currency("INR")
                        .status(AccountStatus.ACTIVE)
                        .build());

        TransferSaga transferSaga = transferSagaRepository.save(
        TransferSaga.builder()
                .senderId(account.getId())
                .receiverId(destinationAccount.getId())
                .amount(new BigDecimal("5000.00"))
                .currency("INR")
                .status(com.paymentledger.command_service.constants.TransferSagaStatus.COMPLETED)
                .build());

        // Write journal entries that sum to 7000
        // (simulating a bug that left balance wrong)
        journalEntryRepository.save(JournalEntry.builder()
                .accountId(account.getId())
                .transferId(transferSaga.getId())
                .entryType(EntryType.CREDIT)
                .amount(new BigDecimal("7000.00"))
                .currency("INR")
                .referenceType("TRANSFER")
                .referenceId(UUID.randomUUID())
                .build());

        // Run reconciliation
        reconciliationService.findAccountsWithBalanceMismatch();

        // Assert account is FROZEN (discrepancy = 3000 > threshold 100)
        Account frozen = accountRepository
                .findById(account.getId()).orElseThrow();
        assertThat(frozen.getStatus())
                .isEqualTo(AccountStatus.FROZEN);

        // Assert reconciliation failure was recorded
        List<ReconciliationFailure> failures =
                reconciliationFailureRepository.findAll();
        assertThat(failures.size()).isEqualTo(2);
        assertThat(failures.get(0).getDiscrepancy())
                .isEqualByComparingTo(new BigDecimal("3000.00"));
        assertThat(failures.get(0).getStatus())
                .isEqualTo("OPEN");
    }
}
