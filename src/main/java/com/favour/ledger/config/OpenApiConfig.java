package com.favour.ledger.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI bankingLedgerOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Banking Ledger API")
                        .version("1.0.0")
                        .description(
                                "Double-entry ledger with pessimistic account locking, "
                                        + "idempotent transfers, and BigDecimal money handling."
                        ));
    }
}
