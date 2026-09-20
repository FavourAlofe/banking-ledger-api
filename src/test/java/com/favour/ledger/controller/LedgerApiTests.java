package com.favour.ledger.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.favour.ledger.PostgresTestConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class LedgerApiTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createAccountTransferAndReadHistory() throws Exception {
        String source = "ACC-10001-" + UUID.randomUUID().toString().substring(0, 8);
        String destination = "ACC-10002-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountNumber":"%s","ownerName":"Jane Doe","openingBalance":100.00}
                                """.formatted(source)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance").value(100.00));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountNumber":"%s","ownerName":"John Smith","openingBalance":0.00}
                                """.formatted(destination)))
                .andExpect(status().isCreated());

        UUID key = UUID.randomUUID();
        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountNumber":"%s","destinationAccountNumber":"%s","amount":40.00,"description":"payroll"}
                                """.formatted(source, destination)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries", hasSize(2)));

        mockMvc.perform(post("/api/v1/transfers")
                        .header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountNumber":"%s","destinationAccountNumber":"%s","amount":40.00,"description":"payroll"}
                                """.formatted(source, destination)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entries", hasSize(2)));

        mockMvc.perform(get("/api/v1/accounts/" + source))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(60.00));

        mockMvc.perform(get("/api/v1/accounts/" + source + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void transferRequiresIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceAccountNumber":"ACC-1","destinationAccountNumber":"ACC-2","amount":1.00}
                                """))
                .andExpect(status().isBadRequest());
    }
}
