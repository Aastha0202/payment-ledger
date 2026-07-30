package com.paymentledger.projection_service.repository;

import com.paymentledger.projection_service.entity.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {

    boolean existsByEventId(UUID eventId);
}
