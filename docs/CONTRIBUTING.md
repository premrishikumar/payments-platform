# Contributing

## Branching Strategy

```
main          ← production-ready
  └── develop ← integration branch
        ├── feature/fps-retry-logic
        ├── feature/bacs-bulk-processor
        └── fix/idempotency-race-condition
```

- Branch from `develop`
- PRs must pass all tests before merge
- Squash commits on merge to `main`

---

## Commit Convention

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add BACS bulk payment processor
fix: handle idempotency race condition under concurrent load
test: add integration test for CHAPS high-value routing
docs: update API reference with new status codes
refactor: extract scheme validation into dedicated validator
```

---

## Running Tests Before PR

```bash
# Must all pass before raising a PR
mvn test

# Check test coverage
mvn verify jacoco:report
open target/site/jacoco/index.html
```

---

## Code Standards

- Java 17 features encouraged (records, switch expressions, text blocks)
- Lombok for boilerplate reduction
- `@Slf4j` for all logging — no `System.out.println`
- All public service methods must have Javadoc
- Integration tests must extend `BaseIntegrationTest`
