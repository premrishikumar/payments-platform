# Payments Platform

Centralised payments platform supporting FPS, CHAPS, and BACS payment schemes.

## Tech Stack
- Java 17 / Spring Boot 3.2
- Apache Kafka (event-driven architecture)
- AWS ECS / DynamoDB (production)
- H2 (local dev)
- Idempotency patterns for safe retries
- PCI-DSS compliant design

## Modules
- `payment-core` — domain models, payment lifecycle
- `kafka` — producers/consumers per scheme
- `idempotency` — deduplication filter
- `controller` — REST API layer
