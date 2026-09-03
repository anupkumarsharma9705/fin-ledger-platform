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
mvn -q -DskipTests=false test
```

## Run a service
```bash
mvn -pl services/identity-service spring-boot:run
mvn -pl services/wallet-service spring-boot:run
```

## Next (Phase 2)
- Ledger postings and invariants
- Idempotency
- Risk decisions
- Outbox + Kafka publishing
