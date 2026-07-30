package com.paymentledger.projection_service.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatementResponse {

    private UUID accountId;
    private List<JournalEntryDTO> entries;

    public static StatementResponse from(UUID accountId, List<JournalEntryDTO> entries) {
        return StatementResponse.builder()
                .accountId(accountId)
                .entries(entries)
                .build();
    }
}
