package com.paymentledger.command_service.service.impl;

import com.paymentledger.command_service.constants.AccountStatus;
import com.paymentledger.command_service.entity.Account;
import com.paymentledger.command_service.entity.ReconciliationFailure;
import com.paymentledger.command_service.metrics.TransferMetrics;
import com.paymentledger.command_service.repository.AccountReconciliationRepository;
import com.paymentledger.command_service.repository.AccountRepository;
import com.paymentledger.command_service.repository.ReconciliationFailureRepository;
import com.paymentledger.command_service.service.ReconciliationService;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.security.auth.login.AccountNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class ReconciliationServiceImpl implements ReconciliationService {

    @Value("${app.reconciliation.discrepancy-threshold:100.00}")
    private BigDecimal discrepancyThreshold;


    @Autowired
    private AccountReconciliationRepository accountReconciliationRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ReconciliationFailureRepository reconciliationFailureRepository;

        @Autowired
        @Lazy
        private ReconciliationServiceImpl self;

        @Autowired
        private TransferMetrics transferMetrics;

        @Override
        @Scheduled(cron = "${app.reconciliation.cron:0 0 0 * * *}")
        public void findAccountsWithBalanceMismatch() {
            log.info("Reconciliation job started at {}",
                    LocalDateTime.now());

            long startTime = System.currentTimeMillis();
            int frozenCount = 0;

            List<Object[]> mismatches = accountReconciliationRepository
                    .findAccountsWithBalanceMismatch();

            if (mismatches.isEmpty()) {
                log.info("Reconciliation complete. " +
                        "No mismatches found. All balances correct.");
                return;
            }

            log.warn("Reconciliation found {} accounts " +
                    "with balance mismatches.", mismatches.size());

            for (Object[] row : mismatches) {
                try {
                    boolean frozen = self.processMismatch(row);
                    if (frozen) frozenCount++;
                } catch (Exception e) {
                    log.error("Failed to process reconciliation " +
                                    "row for account {}: {}",
                            row[0], e.getMessage());
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            transferMetrics.recordReconciliationMismatch();
            log.info("Reconciliation complete. " +
                            "Mismatches: {}, Frozen: {}, Duration: {}ms",
                    mismatches.size(), frozenCount, duration);
        }

        @Transactional
        public boolean processMismatch(Object[] row) throws AccountNotFoundException {
            UUID accountId = (UUID) row[0];
            BigDecimal cachedBalance = (BigDecimal) row[1];
            BigDecimal calculatedBalance = (BigDecimal) row[2];
            BigDecimal discrepancy = cachedBalance
                    .subtract(calculatedBalance).abs();

            log.error("RECONCILIATION MISMATCH — " +
                            "accountId: {}, cached: {}, " +
                            "calculated: {}, discrepancy: {}",
                    accountId, cachedBalance,
                    calculatedBalance, discrepancy);

            // Save failure record
            reconciliationFailureRepository.save(
                    ReconciliationFailure.builder()
                            .accountId(accountId)
                            .cachedBalance(cachedBalance)
                            .calculatedBalance(calculatedBalance)
                            .discrepancy(discrepancy)
                            .status("OPEN")
                            .build());

            // Freeze if above threshold
            if (discrepancy.compareTo(discrepancyThreshold) > 0) {
                Account account = accountRepository
                        .findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(
                                "Account not found: " + accountId));

                account.setStatus(AccountStatus.FROZEN);
                accountRepository.save(account);

                log.error("CRITICAL: Account {} FROZEN " +
                                "due to discrepancy of {} " +
                                "exceeding threshold of {}",
                        accountId, discrepancy, discrepancyThreshold);

                return true;
            }

            log.warn("Account {} flagged for review. " +
                            "Discrepancy {} below freeze threshold.",
                    accountId, discrepancy);

            return false;
        }
}
