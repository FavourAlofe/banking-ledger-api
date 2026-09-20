package com.favour.ledger.controller;

import com.favour.ledger.dto.AccountResponse;
import com.favour.ledger.dto.CreateAccountRequest;
import com.favour.ledger.dto.TransferResponse;
import com.favour.ledger.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open a new account")
    public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
        return AccountResponse.from(accountService.create(
                request.accountNumber(),
                request.ownerName(),
                request.openingBalance()
        ));
    }

    @GetMapping
    @Operation(summary = "List accounts")
    public List<AccountResponse> list() {
        return accountService.list().stream().map(AccountResponse::from).toList();
    }

    @GetMapping("/{accountNumber}")
    @Operation(summary = "Get account by account number")
    public AccountResponse get(@PathVariable String accountNumber) {
        return AccountResponse.from(accountService.getByAccountNumber(accountNumber));
    }

    @GetMapping("/{accountNumber}/transactions")
    @Operation(summary = "List ledger transactions for an account")
    public List<TransferResponse> history(@PathVariable String accountNumber) {
        return accountService.history(accountNumber).stream().map(TransferResponse::from).toList();
    }
}
