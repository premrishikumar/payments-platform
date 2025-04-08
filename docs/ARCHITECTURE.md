# Architecture

## Overview

The Payments Platform is built around an **event-driven architecture** using Apache Kafka
to decouple payment submission from scheme processing. This enables:

- Independent scaling of submission vs processing
- Resilience — Kafka acts as a durable buffer
- Auditability — full event log of payment lifecycle

---

## Component Diagram

```
┌──────────────────────────────────────────────────────────┐
│                     Payments Platform                     │
│                                                           │
│  ┌─────────────┐    ┌──────────────┐   ┌──────────────┐ │
│  │PaymentCtrlr │───►│PaymentService│──►│  SchemeRouter│ │
│  └─────────────┘    └──────┬───────┘   └──────────────┘ │
│                            │                              │
│                  ┌─────────▼──────────┐                  │
│                  │ IdempotencyService  │                  │
│                  └─────────┬──────────┘                  │
│                            │                              │
│              ┌─────────────▼──────────────┐              │
│              │      PaymentRepository      │              │
│              │      (JPA / H2 / PG)        │              │
│              └────────────────────────────┘              │
│                            │                              │
│              ┌─────────────▼──────────────┐              │
│              │     KafkaEventProducer      │              │
│              └─────────────┬──────────────┘              │
│                            │                              │
│         ┌──────────────────▼────────────────────┐        │
│         │            Kafka Topics                │        │
│         │  payment.submitted  payment.failed      │        │
│         └──────────────────┬────────────────────┘        │
│                            │                              │
│              ┌─────────────▼──────────────┐              │
│              │     KafkaEventConsumer      │              │
│              │   (updates payment status)  │              │
│              └────────────────────────────┘              │
└──────────────────────────────────────────────────────────┘
```

---

## Key Design Decisions

### 1. Idempotency via Header Key

Every POST /payments requires an `Idempotency-Key` header (UUID).

**Why:** In distributed systems, network failures cause duplicate retries.
Without idempotency, a retry could result in two payments being sent —
catastrophic in a payments context.

**Implementation:** Key stored in DB. On retry, existing payment is returned
without re-processing. Kafka event is NOT re-published.

```
Client ──► POST /payments [Idempotency-Key: abc]
              │
              ▼
         Key exists?
         YES → return existing payment (no duplicate)
         NO  → persist + publish to Kafka
```

---

### 2. Event-Driven Status Updates

Payment status is not updated synchronously. Instead:

1. Payment saved as `PENDING`
2. `payment.submitted` event published to Kafka
3. Kafka consumer picks up event, updates status to `SUBMITTED`

**Why:** Decouples the API response time from scheme processing latency.
CHAPS processing can take minutes — the client should not wait.

---

### 3. Scheme Routing

Auto-routing logic lives in `SchemeRouter`:

| Condition | Scheme |
|-----------|--------|
| Amount ≤ £1,000,000 | FPS |
| Amount > £1,000,000 | CHAPS |
| Explicitly set | Honoured as-is |

BACS is always explicit — it's a 3-day bulk scheme used for payroll/DD,
not suitable for auto-routing.

---

### 4. Transactional Boundary

`PaymentService.submitPayment()` is `@Transactional`.

- DB write and Kafka publish happen in sequence
- If DB write fails → Kafka event never published (consistent)
- If Kafka publish fails → logged, payment stays `PENDING` (can be reprocessed)

> In production, this uses the **Outbox Pattern** to guarantee exactly-once
> Kafka delivery even if the broker is temporarily unavailable.

---

## Technology Choices

| Technology | Reason |
|------------|--------|
| **Spring Boot 3.2** | Industry standard for Java microservices in fintech |
| **Apache Kafka** | Durable, replayable event log; FPS/CHAPS volumes require async processing |
| **Spring Data JPA** | Clean repository abstraction; easy to swap H2 → PostgreSQL |
| **H2 (dev) / PostgreSQL (prod)** | Fast local iteration; production-grade persistence |
| **Embedded Kafka (tests)** | No external broker needed for CI; deterministic test behaviour |
| **Awaitility** | Clean async assertions in Kafka consumer tests |

---

## Production Considerations

These are not implemented in this repo but are standard in a production deployment:

| Concern | Approach |
|---------|----------|
| Exactly-once Kafka delivery | Outbox Pattern + Debezium CDC |
| High availability | ECS Fargate multi-AZ + ALB |
| Secret management | AWS Secrets Manager |
| Database | Amazon RDS PostgreSQL (Multi-AZ) |
| Observability | Micrometer + CloudWatch / Datadog |
| PCI compliance | No PAN storage; TLS in transit; VPC isolation |
| Schema registry | Confluent Schema Registry for Avro |
