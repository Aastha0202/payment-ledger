package com.paymentledger.command_service.repository;

import com.paymentledger.command_service.entity.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;


public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    // Implement custom methods here

    List<JournalEntry> findByTransferId(UUID transferId);
}
