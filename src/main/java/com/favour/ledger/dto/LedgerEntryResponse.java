package com.favour.ledger.dto;

import com.favour.ledger.model.EntryType;
import com.favour.ledger.model.LedgerEntry;
import java.math.BigDecimal;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID id,
        String accountNumber,
        EntryType type,
        BigDecimal amount
) {

    public static LedgerEntryResponse from(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getAccount().getAccountNumber(),
                entry.getType(),
                entry.getAmount()
        );
    }
}
