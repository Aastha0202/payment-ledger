package com.paymentledger.command_service.entity;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_failures")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationFailure {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "cached_balance",
            precision = 19, scale = 4, nullable = false)
    private BigDecimal cachedBalance;

    @Column(name = "calculated_balance",
            precision = 19, scale = 4, nullable = false)
    private BigDecimal calculatedBalance;

    @Column(name = "discrepancy",
            precision = 19, scale = 4, nullable = false)
    private BigDecimal discrepancy;

    @CreationTimestamp
    @Column(name = "detected_at",
            nullable = false, updatable = false)
    private LocalDateTime detectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "status", nullable = false)
    private String status;
}