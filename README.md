# Payments Platform

Centralised payments platform supporting FPS, CHAPS, and BACS payment schemes.
Built with event-driven architecture using Apache Kafka.

## Tech Stack
- Java 17 / Spring Boot 3.2
- Apache Kafka (event-driven, 3 topics)
- Spring Data JPA / H2 (local), PostgreSQL (prod)
- Docker / Docker Compose
- AWS ECS + DynamoDB (production deployment)

## Architecture
```
Client → REST API → PaymentService → PaymentRepository (DB)
                         ↓
                  KafkaProducer → [payment.submitted]
                         ↓
                  KafkaConsumer ← [payment.processed / payment.failed]
```

## Key Design Decisions
- **Idempotency**: All payment submissions require `Idempotency-Key` header. Safe retries return existing payment.
- **Scheme routing**: FPS for ≤£1M, CHAPS for high-value, BACS for bulk/scheduled.
- **Event-driven**: Payment lifecycle events published to Kafka topics.
- **PCI considerations**: No raw card data stored; audit trail via event log.

## Running Locally
```bash
docker-compose up -d
mvn spring-boot:run
```

## API
```
POST /api/v1/payments          - Submit payment (requires Idempotency-Key header)
GET  /api/v1/payments/{id}     - Get payment by ID
GET  /api/v1/payments?status=  - Filter by status
GET  /actuator/health          - Health check
```
