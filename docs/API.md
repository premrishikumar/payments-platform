# API Reference

Base URL: `http://localhost:8080/api/v1`

All requests must use `Content-Type: application/json`.

---

## Authentication

> This is a local development build. Production deployments use OAuth2 / mTLS.

---

## Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Content-Type` | Yes | `application/json` |
| `Idempotency-Key` | Yes (POST) | Unique UUID per payment request. Safe retry key. |

---

## Endpoints

### POST /payments

Submit a new payment.

**Headers:**
```
Idempotency-Key: <uuid>
Content-Type: application/json
```

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `scheme` | `FPS` \| `CHAPS` \| `BACS` | No | Payment scheme. Auto-routed if omitted. |
| `amount` | `number` | Yes | Payment amount (GBP) |
| `currency` | `string` | Yes | ISO 4217 currency code e.g. `GBP` |
| `debtorAccountNumber` | `string` | Yes | 8-digit account number |
| `debtorSortCode` | `string` | Yes | 6-digit sort code |
| `creditorAccountNumber` | `string` | Yes | 8-digit account number |
| `creditorSortCode` | `string` | Yes | 6-digit sort code |
| `reference` | `string` | No | Payment reference (max 18 chars for FPS) |

**Example Request:**
```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: a1b2c3d4-e5f6-7890-abcd-ef1234567890" \
  -d '{
    "scheme": "FPS",
    "amount": 500.00,
    "currency": "GBP",
    "debtorAccountNumber": "12345678",
    "debtorSortCode": "200000",
    "creditorAccountNumber": "87654321",
    "creditorSortCode": "400000",
    "reference": "Rent-March-2025"
  }'
```

**Response — 201 Created:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "idempotencyKey": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "scheme": "FPS",
  "status": "PENDING",
  "amount": 500.00,
  "currency": "GBP",
  "debtorAccountNumber": "12345678",
  "debtorSortCode": "200000",
  "creditorAccountNumber": "87654321",
  "creditorSortCode": "400000",
  "reference": "Rent-March-2025",
  "createdAt": "2025-03-10T09:00:00Z",
  "updatedAt": "2025-03-10T09:00:00Z"
}
```

**Error Responses:**

| Status | Reason |
|--------|--------|
| `400 Bad Request` | Missing required fields or `Idempotency-Key` header |
| `409 Conflict` | Idempotency key already used with different payload |

---

### GET /payments/{id}

Retrieve a payment by its UUID.

**Example:**
```bash
curl http://localhost:8080/api/v1/payments/550e8400-e29b-41d4-a716-446655440000
```

**Response — 200 OK:** *(same structure as POST response)*

**Error Responses:**

| Status | Reason |
|--------|--------|
| `404 Not Found` | No payment found for given ID |

---

### GET /payments?status={status}

Filter payments by status.

**Query Parameters:**

| Param | Values | Description |
|-------|--------|-------------|
| `status` | `PENDING` \| `SUBMITTED` \| `ACCEPTED` \| `REJECTED` \| `SETTLED` \| `FAILED` | Filter by payment lifecycle status |

**Example:**
```bash
# Get all pending payments
curl http://localhost:8080/api/v1/payments?status=PENDING

# Get all failed payments
curl http://localhost:8080/api/v1/payments?status=FAILED
```

**Response — 200 OK:**
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "scheme": "FPS",
    "status": "PENDING",
    "amount": 500.00,
    ...
  }
]
```

---

### GET /actuator/health

Returns application and dependency health status.

```bash
curl http://localhost:8080/actuator/health
```

**Response — 200 OK:**
```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "kafka": { "status": "UP" },
    "paymentsHealth": {
      "status": "UP",
      "details": {
        "service": "payments-platform",
        "schemes": "FPS, CHAPS, BACS"
      }
    }
  }
}
```

---

## Payment Lifecycle

```
PENDING → SUBMITTED → ACCEPTED → SETTLED
                    ↘ REJECTED
         ↘ FAILED
```

| Status | Description |
|--------|-------------|
| `PENDING` | Payment received, persisted, Kafka event published |
| `SUBMITTED` | Kafka consumer confirmed submission to scheme |
| `ACCEPTED` | Scheme accepted the payment |
| `REJECTED` | Scheme rejected (insufficient funds, invalid account, etc.) |
| `SETTLED` | Funds transferred successfully |
| `FAILED` | Processing error — safe to retry with same idempotency key |

---

## Scheme Auto-Routing

If `scheme` is omitted from the request, the platform routes automatically:

| Amount | Routed To |
|--------|-----------|
| ≤ £1,000,000 | FPS |
| > £1,000,000 | CHAPS |

BACS must always be explicitly set (bulk/payroll use case).
