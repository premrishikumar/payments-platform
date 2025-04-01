# Local Setup Guide

Step-by-step instructions to run the Payments Platform on your machine.

---

## Prerequisites

Install the following before proceeding:

| Tool | Version | Install |
|------|---------|---------|
| Java JDK | 17+ | [adoptium.net](https://adoptium.net) |
| Apache Maven | 3.8+ | [maven.apache.org](https://maven.apache.org) |
| Docker Desktop | 20+ | [docker.com](https://www.docker.com/products/docker-desktop) |
| curl / Postman | any | For API testing |

Verify installations:

```bash
java -version    # openjdk 17 or higher
mvn -version     # Apache Maven 3.8+
docker -version  # Docker version 20+
```

---

## 1. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/payments-platform.git
cd payments-platform
```

---

## 2. Start Infrastructure (Kafka + Zookeeper)

```bash
docker-compose up -d
```

This starts:
- **Zookeeper** on port `2181`
- **Kafka broker** on port `9092`

Wait ~15 seconds for Kafka to be fully ready. Verify:

```bash
docker-compose ps
# Both services should show "Up"
```

---

## 3. Run the Application

```bash
mvn spring-boot:run
```

Expected output:
```
Started PaymentsPlatformApplication in 3.4 seconds
Kafka topics created: payment.submitted, payment.processed, payment.failed
```

Application is available at: `http://localhost:8080`

---

## 4. Verify the Application is Running

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
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

## 5. Submit a Test Payment

```bash
curl -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: IK-$(uuidgen)" \
  -d '{
    "scheme": "FPS",
    "amount": 250.00,
    "currency": "GBP",
    "debtorAccountNumber": "12345678",
    "debtorSortCode": "200000",
    "creditorAccountNumber": "87654321",
    "creditorSortCode": "400000",
    "reference": "Invoice-001"
  }'
```

---

## 6. Run Tests

```bash
# All tests (unit + integration)
mvn test

# Integration tests only — uses embedded Kafka, no Docker needed
mvn test -Dtest="*IntegrationTest"
```

---

## 7. Stop Everything

```bash
# Stop the app: Ctrl+C

# Stop Docker containers
docker-compose down

# Stop and remove volumes (clean slate)
docker-compose down -v
```

---

## Troubleshooting

### Port 9092 already in use
```bash
# Find and stop the conflicting process
lsof -i :9092
kill -9 <PID>
```

### Kafka topics not created on startup
```bash
# Restart Kafka cleanly
docker-compose down -v
docker-compose up -d
# Wait 20 seconds, then re-run the app
```

### Java version mismatch
```bash
# macOS — switch to Java 17
export JAVA_HOME=$(/usr/libexec/java_home -v 17)

# Linux
sudo update-alternatives --config java
```

### H2 console (inspect in-memory DB)
Add to `application.yml` for local debugging:
```yaml
spring:
  h2:
    console:
      enabled: true
```
Then visit: `http://localhost:8080/h2-console`
JDBC URL: `jdbc:h2:mem:paymentsdb`
