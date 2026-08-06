package com.paymentledger.command_service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
public class TransferMetrics {

    private final MeterRegistry meterRegistry;

    public void recordTransferSuccess(String currency) {
        Counter.builder("payment.transfers.total")
                .tag("status", "success")
                .tag("currency", currency)
                .description("Total number of transfers")
                .register(meterRegistry)
                .increment();
    }

    public void recordTransferFailure(String reason) {
        Counter.builder("payment.transfers.total")
                .tag("status", "failure")
                .tag("reason", reason)
                .description("Total number of transfers")
                .register(meterRegistry)
                .increment();
    }

    public void recordTransferDuration(long milliseconds) {
        Timer.builder("payment.transfers.duration")
                .description("Transfer processing duration")
                .register(meterRegistry)
                .record(Duration.ofMillis(milliseconds));
    }

    public void recordCompensation() {
        Counter.builder("payment.compensations.total")
                .description("Total number of saga compensations")
                .register(meterRegistry)
                .increment();
    }

    public void recordReconciliationMismatch() {
        Counter.builder("payment.reconciliation.mismatches")
                .description("Total reconciliation mismatches found")
                .register(meterRegistry)
                .increment();
    }
}
