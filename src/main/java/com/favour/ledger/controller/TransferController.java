package com.favour.ledger.controller;

import com.favour.ledger.dto.TransferRequest;
import com.favour.ledger.dto.TransferResponse;
import com.favour.ledger.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transfers")
public class TransferController {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Post a double-entry transfer")
    public TransferResponse transfer(
            @Parameter(
                    name = IDEMPOTENCY_KEY_HEADER,
                    in = ParameterIn.HEADER,
                    required = true,
                    description = "Client-generated UUID. Retries with the same key return the original journal.",
                    schema = @Schema(format = "uuid")
            )
            @RequestHeader(IDEMPOTENCY_KEY_HEADER) UUID idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        return TransferResponse.from(transferService.transfer(
                idempotencyKey,
                request.sourceAccountNumber(),
                request.destinationAccountNumber(),
                request.amount(),
                request.description()
        ));
    }

    @GetMapping("/transactions/{id}")
    @Operation(summary = "Get a ledger transaction by id")
    public TransferResponse get(@PathVariable UUID id) {
        return TransferResponse.from(transferService.getById(id));
    }
}
