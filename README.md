# Banking Ledger API

Double-entry ledger service for a TD Securities TAP demonstration. Transfers always post a matching **debit** and **credit**, money is `BigDecimal` (never `double`), and concurrent transfers lock accounts in a fixed order so they cannot deadlock or overdraft.

## Stack

- Java 21, Spring Boot 4, Maven
- PostgreSQL 18 (Docker Compose)
- Spring Data JPA, Bean Validation, SpringDoc OpenAPI

## Run locally

```bash
docker compose up -d
./mvnw spring-boot:run
```

Postgres is published on **5433** (`ledger` / `ledger`, database `ledger_db`).

API base: `http://localhost:8080`

### Swagger UI

Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).

**Swagger UI** is an interactive HTML page generated from the API's OpenAPI document (`/v3/api-docs`). It lists every endpoint, shows request/response schemas, and lets you click **Try it out** to call the live server. It is documentation you can execute — not a separate product from the API.

Machine-readable spec: `GET /v3/api-docs`

## Example flow

Create two dummy accounts, then transfer with an `Idempotency-Key` (any UUID you generate). Sending the same key twice returns the original journal instead of moving money again.

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

## Design notes

| Concern | Approach |
| --- | --- |
| Precision | `BigDecimal` scale 2, `RoundingMode.HALF_EVEN` |
| Double-entry | One `LedgerTransaction` with debit + credit legs; persist is rejected if unbalanced |
| Immutability | Journal tables are Hibernate `@Immutable`; only `Account.balance` changes |
| Isolation | `PESSIMISTIC_WRITE` on both accounts inside one transaction |
| Deadlocks | Lock the two account numbers in sorted string order |
| Retries | Unique `idempotency_key`; identical retries replay, different payload → `409` |
| Overdraft | Concurrent debit test against real PostgreSQL, not mocks |

## Tests

```bash
./mvnw test
```

Requires Docker (Testcontainers starts PostgreSQL 18).

## Privacy

Sample data only (`ACC-10001`, `Jane Doe`). Database credentials come from `DB_USERNAME` / `DB_PASSWORD` (Compose defaults are dummy `ledger` / `ledger` on port **5433** so a local Postgres on 5432 is left alone).
