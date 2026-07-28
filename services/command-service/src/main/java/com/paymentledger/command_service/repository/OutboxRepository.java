package com.paymentledger.command_service.repository;

import com.paymentledger.command_service.constants.OutboxStatus;
import com.paymentledger.command_service.entity.Outbox;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.awt.print.Pageable;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<Outbox, UUID> {

    // What the interface needs (method signature — this is how you DECLARE it):
    Page<Outbox> findByStatusOrderByCreatedAtAsc(
            OutboxStatus status, Pageable pageable);

    List<Outbox> findByStatusAndRetryCountLessThan(
            OutboxStatus status, int maxRetryCount);
}
