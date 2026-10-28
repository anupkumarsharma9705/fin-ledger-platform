# Fin Ledger Platform (Microservices)

Fintech-style backend platform using Spring Boot microservices.

## Phase 1 Scope
- Maven multi-module foundation
- Identity service (JWT auth)
- Wallet service (wallet + balance)
- Scaffold services: ledger, payment, risk, notification, reconciliation, api-gateway
- Infra with Docker Compose (Postgres, Redis, Kafka, Zookeeper)

## Architecture (Phase 1)
- services/identity-service
- services/wallet-service
- services/ledger-service
- services/payment-service
- services/risk-service
- services/notification-service
- services/reconciliation-service
- services/api-gateway

## Prerequisites
- Java 21
- Maven 3.9+
- Docker + Docker Compose

## Run Infra
```bash
docker compose up -d
```

## Build
```bash
mvn -q -f services/identity-service/pom.xml test
mvn -q -f services/wallet-service/pom.xml test
```

## Run a service
```bash
mvn -pl services/identity-service spring-boot:run
mvn -pl services/wallet-service spring-boot:run
```

## Ledger model (wallet-service)
- Double-entry posting API is available at `POST /api/v1/ledger/entries`.
- Every entry is append-only and written to:
  - `wallet_service.ledger_entries`
  - `wallet_service.ledger_lines`
- Invariants enforced in service + schema:
  - At least 2 lines per entry
  - Must include both debit and credit directions
  - Sum(debits) == Sum(credits) for each entry
  - Currency is explicit on each entry and line
  - Cross-currency postings are rejected
  - Posted entries are immutable; reversals/refunds create compensating entries
- Lifecycle:
  - `PENDING` (authorization hold)
  - `POSTED` (affects balances)
  - `VOIDED` (pending canceled, no balance impact)
- Reversal APIs:
  - `POST /api/v1/ledger/entries/{entryId}/reverse`
  - `POST /api/v1/ledger/entries/{entryId}/refund`
- Pending lifecycle APIs:
  - `POST /api/v1/ledger/entries/{entryId}/post`
  - `POST /api/v1/ledger/entries/{entryId}/void`

## Idempotency and transactional guarantees
- `Idempotency-Key` header is required for posting/reversal/refund writes.
- Replayed requests with the same key + same request payload return the original entry.
- Reusing a key with a different payload returns `409 Conflict`.
- Posting and wallet balance updates run in one DB transaction with pessimistic account locks to prevent partial writes and race conditions.
- Duplicate external references and duplicate compensating entries are blocked by DB constraints.

## Auditability
- Entries store `request_id`, `correlation_id`, `actor`, `source`, timestamps, and optional external reference.
- Each ledger line links back to wallet and parent journal entry for reconciliation-friendly queries.

## Balance semantics
- Wallet balance is materialized in `wallet_service.wallets.balance`.
- Credits increase wallet balance; debits decrease wallet balance.
- Negative balances are rejected at post time.

## Known limitations
- Reversal/refund is full-amount (not partial) in the current implementation.
- JWT subject currently carries user email (not wallet owner UUID), so ownership checks for arbitrary wallet queries are not yet enforced.
