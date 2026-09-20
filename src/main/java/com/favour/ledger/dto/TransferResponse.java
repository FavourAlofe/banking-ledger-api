package com.favour.ledger.dto;

import com.favour.ledger.model.LedgerTransaction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        UUID idempotencyKey,
        String description,
        Instant createdAt,
        List<LedgerEntryResponse> entries
) {

    public static TransferResponse from(LedgerTransaction transaction) {
        return new TransferResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getCreatedAt(),
                transaction.getEntries().stream().map(LedgerEntryResponse::from).toList()
        );
    }
}
