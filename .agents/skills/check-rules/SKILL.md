---
name: check-rules
description: Check the hard rules from AGENTS.md before committing in epos-api. Use before each commit or when finishing a step.
---

# Check project rules before committing

This skill only REPORTS violations. It never changes code.

## Steps

1. Run `./mvnw test` and report the result.
2. Check each rule below and report violations with file and line:

   **Database**
   - Existing Flyway migrations were not modified
     (`git diff` on `src/main/resources/db/migration/`).
   - `ddl-auto` is `validate` in every profile.
   - Dev seed data is not in the default migration location.

   **Data**
   - No `double` or `float` used for money.
   - Money columns are `BIGINT`, timestamps are `TIMESTAMPTZ`.

   **Architecture**
   - No `domain/`, `port/`, `application/`, or use-case classes anywhere.
   - Only `SaleCalculator` and `InvoiceNumberGenerator` are separated out
     of `SaleService`.
   - Controllers contain no business logic and do not call repositories.
   - No JPA entity is returned from a controller.

   **Security**
   - No secrets hard-coded (JWT secret, database password).
   - `.env` is listed in `.gitignore`.
   - No response exposes `password_hash`, or `cost_price` for CASHIER.

   **Testing**
   - No H2 dependency or H2 configuration.
   - New business logic touching stock or money has tests.

   **Dependencies**
   - No new dependency in `pom.xml` that was not requested.

3. Do not modify any file.

## Output format

Test result first, then a checklist: passed items in one line, violations
listed with location. If everything passes, say so in one line.
