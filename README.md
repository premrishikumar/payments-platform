# 💳 Payments Platform

> Centralised payments platform supporting **FPS**, **CHAPS**, and **BACS** schemes.
> Built with event-driven architecture using Apache Kafka, Spring Boot 3.2, and Java 17.

[![Java](https://img.shields.io/badge/Java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)](https://spring.io/projects/spring-boot)
[![Kafka](https://img.shields.io/badge/Apache%20Kafka-event--driven-orange)](https://kafka.apache.org/)
[![License](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Getting Started](#getting-started)
- [API Reference](#api-reference)
- [Payment Schemes](#payment-schemes)
- [Idempotency](#idempotency)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)

---

## Overview

This platform provides a unified API for submitting and tracking payments across UK payment schemes.
Key design goals:

- **Idempotent submissions** — safe retries via `Idempotency-Key` header
- **Event-driven** — Kafka topics decouple submission from processing
- **Scheme routing** — automatic FPS/CHAPS routing based on amount
- **PCI-conscious** — no raw card data stored; full audit trail via events

---

## Architecture

```
┌─────────────┐     POST /payments      ┌──────────────────┐
│   Client    │ ──────────────────────► │  PaymentController│
└─────────────┘   Idempotency-Key hdr   └────────┬─────────┘
                                                 │
                                    ┌────────────▼────────────┐
                                    │     PaymentService       │
                                    │  ┌────────────────────┐  │
                                    │  │ IdempotencyService  │  │
                                    │  └────────────────────┘  │
                                    │  ┌────────────────────┐  │
                                    │  │   SchemeRouter      │  │
                                    │  └────────────────────┘  │
                                    └──────┬──────────┬────────┘
                                           │          │
                              ┌────────────▼─┐   ┌───▼──────────────┐
                              │  PostgreSQL   │   │  Kafka Producer   │
                              │  (payments)   │   │payment.submitted  │
                              └──────────────┘   └───────┬──────────┘
                                                         │
                                                ┌────────▼──────────┐
                                                │  Kafka Consumer    │
                                                │  → update status   │
                                                └────────────────────┘
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for full design decisions.

---

## Getting Started

### Prerequisites

| Tool | Version |
|------|---------|
| Java | 17+ |
| Maven | 3.8+ |
| Docker | 20+ |

### Run locally

```bash
# 1. Start Kafka
docker-compose up -d

# 2. Run the application
mvn spring-boot:run
```

App starts on `http://localhost:8080`

See [docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md) for full setup guide.

---

## API Reference

See [docs/API.md](docs/API.md) for full API documentation.

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/v1/payments` | Submit a payment |
| `GET` | `/api/v1/payments/{id}` | Get payment by ID |
| `GET` | `/api/v1/payments?status=` | Filter payments by status |
| `GET` | `/actuator/health` | Health check |

---

## Payment Schemes

| Scheme | Use Case | Limit | Clearing |
|--------|----------|-------|----------|
| **FPS** | Everyday payments | ≤ £1,000,000 | Near real-time |
| **CHAPS** | High-value, same-day | No limit | Same day |
| **BACS** | Bulk / payroll | No limit | 3 days |

---

## Idempotency

All `POST /payments` requests **must** include an `Idempotency-Key` header.

- Same key = same payment returned (no duplicate created)
- Safe to retry on network failure
- Key should be a UUID generated client-side

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Idempotency-Key: $(uuidgen)" \
  ...
```

---

## Running Tests

```bash
# All tests
mvn test

# Integration tests only
mvn test -Dtest="*IntegrationTest"

# Unit tests only
mvn test -Dtest="*Test" -Dtest="!*IntegrationTest"
```

Integration tests use **embedded Kafka** — no Docker required for tests.

---

## Project Structure

```
src/
├── main/java/com/payments/platform/
│   ├── config/          # Kafka, Actuator config
│   ├── controller/      # REST endpoints
│   ├── domain/          # Payment, PaymentScheme, PaymentStatus
│   ├── exception/       # Custom exceptions + global handler
│   ├── idempotency/     # Duplicate detection
│   ├── kafka/           # Producer + Consumer
│   ├── repository/      # JPA repository
│   └── service/         # PaymentService, SchemeRouter
└── test/java/com/payments/platform/
    ├── IdempotencyServiceTest.java
    └── integration/
        ├── BaseIntegrationTest.java
        ├── PaymentSubmissionIntegrationTest.java
        ├── PaymentRetrievalIntegrationTest.java
        ├── PaymentKafkaIntegrationTest.java
        ├── IdempotencyIntegrationTest.java
        ├── SchemeRoutingIntegrationTest.java
        └── ActuatorIntegrationTest.java
```
