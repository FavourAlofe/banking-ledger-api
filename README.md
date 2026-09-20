# Banking Ledger API

Spring Boot service for moving money between accounts. Each transfer writes one debit and one credit. Amounts use `BigDecimal`. Concurrent transfers lock both accounts in a fixed order so they don't deadlock or overdraft. Retries send the same `Idempotency-Key` and get the original journal back instead of posting twice.

Java 21, PostgreSQL, Maven.

## Run

Needs Docker Desktop and Java 21+.

```bash
docker compose up -d
./mvnw spring-boot:run
```

Postgres listens on port **5433** (user/password/db: `ledger` / `ledger` / `ledger_db`). The API is `http://localhost:8080`.

```bash
curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"accountNumber":"ACC-10001","ownerName":"Jane Doe","openingBalance":100.00}'

curl -s -X POST http://localhost:8080/api/v1/accounts \
  -H 'Content-Type: application/json' \
  -d '{"accountNumber":"ACC-10002","ownerName":"John Smith","openingBalance":0.00}'

curl -s -X POST http://localhost:8080/api/v1/transfers \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"sourceAccountNumber":"ACC-10001","destinationAccountNumber":"ACC-10002","amount":25.50,"description":"payroll"}'
```

```bash
./mvnw test
```

Tests start their own Postgres with Testcontainers.

To wipe local accounts and start over: `docker compose down -v`, then `docker compose up -d` again.
