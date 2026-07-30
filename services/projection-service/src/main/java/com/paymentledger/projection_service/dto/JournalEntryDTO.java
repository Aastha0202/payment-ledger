package com.paymentledger.projection_service.dto;

import com.paymentledger.projection_service.constants.EntryType;
import com.paymentledger.projection_service.entity.JournalEntry;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryDTO {

    private UUID transferId;
    private EntryType entryType; // "debit" or "credit"
    private BigDecimal amount;
    private String currency;
    private String description;
    private LocalDateTime createdAt;


    public static JournalEntryDTO from(JournalEntry journalEntry) {
        return JournalEntryDTO.builder()
                .transferId(journalEntry.getTransferId())
                .entryType(journalEntry.getEntryType())
                .amount(journalEntry.getAmount())
                .currency(journalEntry.getCurrency())
                .description(journalEntry.getDescription())
                .createdAt(journalEntry.getCreatedAt())
                .build();
    }

}
