package com.paymentledger.command_service.repository;

import com.paymentledger.command_service.entity.ReconciliationFailure;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReconciliationFailureRepository
        extends JpaRepository<ReconciliationFailure, UUID> {

    List<ReconciliationFailure> findByStatus(String status);
}
