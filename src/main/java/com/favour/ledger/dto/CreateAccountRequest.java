package com.favour.ledger.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateAccountRequest(
        @NotBlank String accountNumber,
        @NotBlank String ownerName,
        @NotNull
        @DecimalMin(value = "0.00", inclusive = true)
        @Digits(integer = 17, fraction = 2)
        BigDecimal openingBalance
) {
}
