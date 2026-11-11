# ePOS API — Project Guide

Backend REST API for a point-of-sale (POS) app for a toko kelontong (small grocery store).

The Angular frontend lives in separate repos (`epos-web-ngmodule`, and later a standalone `epos-web`) and talks to this REST API **only**. The frontend is almost finished, built on the contract in `docs/api-spec.md`.

Reference documents: `docs/PRD-ePOS-Toko-Kelontong.md` (data model in section 14) and `docs/api-spec.md`.

---

## 0. The API contract is already in use by the frontend

`docs/api-spec.md` is not a draft; it is a contract that has **already been implemented on the frontend side**. Consequences:

- Field names, response shapes, HTTP codes, and error `code` values **must not change** without a strong reason.
- If an endpoint truly must change, update `docs/api-spec.md` in the same commit and state the change explicitly in the commit message so the frontend can adapt.
- Do not "fix" naming to make it tidier (for example `change_amount` → `change`) just because it looks better.

---

## 1. Tech Stack

| Part         | Choice                                    |
| ------------ | ----------------------------------------- |
| Language     | Java 21                                   |
| Framework    | Spring Boot 3.3.x                         |
| Build tool   | Maven                                     |
| Database     | PostgreSQL 16                             |
| ORM          | Spring Data JPA (Hibernate)               |
| DB migration | Flyway                                    |
| Security     | Spring Security + JWT (jjwt)              |
| Validation   | Bean Validation (Jakarta)                 |
| API docs     | springdoc-openapi (Swagger UI)            |
| DTO mapping  | MapStruct                                 |
| Boilerplate  | Lombok                                    |
| Testing      | JUnit 5, AssertJ, Testcontainers, MockMvc |
| Container    | Docker + Docker Compose                   |

**Dependency rule:** do not add new dependencies without a clear reason. Prefer what Spring Boot already provides.

---

## 2. Architecture

All packages use the same **layered architecture**.

```
Controller  →  Service  →  Repository  →  Database
   (DTO)       (logic)      (entity)
```

This project deliberately does **not** use clean architecture (separate domain model, ports/adapters, use cases). For an app this size, the extra layers mostly just produce mappers and boilerplate. Record this decision in the README as a conscious trade-off.

Rules that must not be broken:

1. **Controllers contain no business logic.** They only receive requests, validate their shape, call a service, and return a response.
2. **JPA entities never leave the service layer.** Controllers always accept and return DTOs.
3. **Repositories are only called from services**, never directly from controllers.
4. **Business logic lives in services**, not in controllers or entities.
5. **No dependency cycles between services.** If you need data from another feature, call its service, not its repository.

### Small exception in `sale`

`SaleService` holds the densest business rules: stock validation, total recalculation, payment check, invoice numbering. To keep it quickly testable, **extract the pure calculations** into helper classes with no Spring dependencies in the `sale` package:

- `SaleCalculator` — computes per-item subtotal, total, and change.
- `InvoiceNumberGenerator` — format `INV-YYYYMMDD-NNNN`.

Both are plain classes without annotations, tested with unit tests without a database. Transactions, locking, and persistence stay in `SaleService`. Do not expand this into a domain layer or use cases.

---

## 3. Package Structure

Package-by-feature, not package-by-layer.

```
com.yoedi.epos
├── EposApplication.java
├── config/          SecurityConfig, OpenApiConfig, JacksonConfig, CorsConfig
├── common/
│   ├── exception/   BusinessException, NotFoundException, GlobalExceptionHandler
│   ├── dto/         PageResponse
│   └── entity/      BaseEntity (id, createdAt, updatedAt)
├── auth/            AuthController, AuthService, JwtService, dto/
├── user/            User, UserRepository, UserService, dto/
├── category/        Category, CategoryRepository, CategoryService, CategoryController, dto/, mapper/
├── product/         Product, ProductRepository, ProductService, ProductController, dto/, mapper/
├── stock/           StockMovement, StockMovementRepository, StockService, StockController, dto/
├── sale/            Sale, SaleItem, SaleRepository, SaleService, SaleController,
│                    SaleCalculator, InvoiceNumberGenerator, dto/, mapper/
├── report/          ReportService, ReportController, dto/
└── store/           StoreSettings, StoreSettingsService, StoreSettingsController
```

---

## 4. Naming Conventions

| Kind                  | Pattern                     | Example                             |
| --------------------- | --------------------------- | ----------------------------------- |
| DB tables & columns   | `snake_case`, plural tables | `sale_items`, `sell_price`          |
| JPA entity            | singular `PascalCase`       | `SaleItem`                          |
| Java field            | `camelCase`                 | `sellPrice`                         |
| JSON request/response | `snake_case`                | `"sell_price": 36500`               |
| Request DTO           | `<Action><Entity>Request`   | `CreateProductRequest`              |
| Response DTO          | `<Entity>Response`          | `ProductResponse`                   |
| Repository method     | `findBy...`, `existsBy...`  | `findByBarcode`                     |
| Flyway migration file | `V<n>__description.sql`     | `V1__init.sql`                      |
| Git branch            | `feat/`, `fix/`, `chore/`   | `feat/sale-transaction`             |
| Commit                | Conventional Commits        | `feat(sale): add checkout endpoint` |

The `camelCase` (Java) to `snake_case` (JSON) conversion is configured once via `PropertyNamingStrategies.SNAKE_CASE` in `JacksonConfig`, not with `@JsonProperty` on every field.

---

## 5. Data Rules (mandatory)

Summary of PRD section 14.4:

- **Money** is stored as `long` / `BIGINT` in rupiah, no decimals. **`double` and `float` are forbidden.**
- **`products.stock` is a cache.** The source of truth for stock is the `stock_movements` table. Both are updated in the same transaction.
- **Stock must never be negative**, enforced by `CHECK (stock >= 0)` in the DB and validation in the domain.
- **`sale_items` stores a snapshot** of `product_name`, `unit`, and `price` at the time of the transaction.
- **No hard deletes** for products and users; use `is_active`. Foreign keys to `products` and `users` use `ON DELETE RESTRICT`.
- **The transaction total is always recalculated on the server.** The total from the client is never trusted; the client only sends `product_id` and `qty`.
- **Time** is stored as `timestamptz` (`OffsetDateTime`). Daily reports use the `Asia/Jakarta` zone.
- **Invoice number** format `INV-YYYYMMDD-NNNN`, sequence resets every day.

---

## 6. Database & Migrations

- The schema is changed **only** through Flyway migration files in `src/main/resources/db/migration/`.
- `spring.jpa.hibernate.ddl-auto=validate`. **Never** use `update` or `create`.
- A committed migration file **must not be edited** (Flyway stores a checksum). Make changes as new files (`V2__...`, `V3__...`).
- Development seed data — user `rina` (CASHIER) and `budi` (OWNER), categories, and sample products from PRD section 12 — goes in `V2__seed_dev_data.sql` and is active only in the `dev` profile. Seed passwords match the ones used by the frontend mock.

---

## 7. Sales Transaction (the most critical part)

`SaleService.checkout()` must follow this order inside a single `@Transactional`:

1. Fetch all purchased products with a **pessimistic write lock**
   (`@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository, equivalent to `SELECT ... FOR UPDATE`).
   **Fetch in `product_id` order** to avoid deadlocks when two transactions buy the same products in different orders.
2. Validate that products are active and stock is sufficient. If not, throw `ProductInactiveException` or `InsufficientStockException`.
3. Compute `subtotal` and `total` via `SaleCalculator`, using prices from the database — not from the request.
4. Validate `paid >= total`; if short, throw `InsufficientPaymentException`. Compute the change.
5. Generate `invoice_no` via `InvoiceNumberGenerator`.
6. Save `sales` and `sale_items`, with a snapshot of `product_name`, `unit`, and `price`.
7. Decrease `products.stock` and record one `stock_movements` row of type `SALE` per item, including `stock_before`, `stock_after`, and `sale_id`.

Any exception rolls back all steps, including invoice numbering. **Do not** catch an exception midway and continue.

---

## 8. Security

- Stateless JWT authentication. The token contains `sub` (user id), `username`, `role`, `exp`.
- Access token lifetime is 8 hours (one work shift). There is no refresh token in v1.
- Passwords are hashed with BCrypt (strength 10).
- Authorization with `@PreAuthorize("hasRole('OWNER')")`. Per-role limits are in `docs/api-spec.md` under "Access rights summary".
- `CASHIER` on `GET /sales` receives only their own transactions for today, **regardless of any filter sent** — filtered on the server, not in the frontend.
- Public endpoints are only `POST /api/auth/login`, Swagger UI, and actuator health.
- **Never** return `password_hash`, or `cost_price` for the `CASHIER` role.
- Secret configuration (JWT secret, DB credentials) goes through environment variables, **never** hardcoded or committed.
- **CORS:** during development the frontend uses the Angular proxy, so CORS is not needed. For the `prod` profile, set allowed origins via environment variable in `CorsConfig` — never `allowedOrigins("*")` together with credentials.

---

## 9. Response & Error Format

Successful responses return the DTO object directly (no `{data: ...}` wrapper). Paginated lists use `PageResponse`.

Errors use `ProblemDetail` (RFC 7807), handled centrally in `GlobalExceptionHandler`:

```json
{
  "type": "about:blank",
  "title": "Stok tidak mencukupi",
  "status": 422,
  "detail": "Stok Minyak Goreng 2 L tersisa 3",
  "code": "INSUFFICIENT_STOCK",
  "timestamp": "2026-09-16T14:32:10+07:00"
}
```

HTTP status mapping:

| Situation                                                                           | Status |
| ----------------------------------------------------------------------------------- | ------ |
| Request validation failed                                                           | 400    |
| Invalid / missing token                                                             | 401    |
| Role not authorized                                                                 | 403    |
| Data not found                                                                      | 404    |
| Data conflict (duplicate SKU/barcode, category still in use)                        | 409    |
| Business rule violated (insufficient stock, insufficient payment, inactive product) | 422    |
| Unexpected error                                                                    | 500    |

- The `code` field is **required on every error** and its value must match exactly the list in `docs/api-spec.md`. The frontend uses this value to decide how to display it: inline in a form, inline in the cart, or as a toast.
- The `title` and `detail` messages are written in **Bahasa Indonesia** because they are shown directly to users.
- Validation errors include an `errors` array of `{ field, message }`.
- Every new domain exception in `sale` must have its mapping registered in `GlobalExceptionHandler`; never let one leak as a 500.

---

## 10. Testing

Testing priorities, not chasing coverage numbers:

1. **Unit tests for `SaleCalculator` and `InvoiceNumberGenerator`** — no Spring, no database, finish in milliseconds. Must cover: subtotal and total calculation, change, and invoice number format including day rollover.
2. **Integration tests for `SaleService.checkout()`** using Testcontainers PostgreSQL. Must cover: normal transaction, empty cart, insufficient stock, insufficient payment, inactive product, and **two concurrent transactions on the same product** (proving the pessimistic lock works).
3. **MockMvc tests** for controllers: status codes, response shape (including `code` on errors), and per-role authorization rules.

Rule: **do not use H2** as a substitute for PostgreSQL in tests, because the behavior of `SELECT ... FOR UPDATE` and its data types differ from production.

---

## 11. Running the Project

```bash
docker compose up -d db      # PostgreSQL on port 5432
./mvnw spring-boot:run       # API on port 8080, dev profile
./mvnw test                  # all tests
./mvnw verify                # test + build
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Profiles: `dev` (local default), `test` (Testcontainers), `prod`.
- The frontend runs on port 4200 and proxies `/api/*` to port 8080.

---

## 12. Implementation Order

Follows `docs/api-spec.md` section 10:

| Stage | Content                                                                                                            |
| ----- | ------------------------------------------------------------------------------------------------------------------ |
| 1     | Project setup, `V1__init.sql`, entities, CRUD for `/categories` and `/products` (no security yet, test in Swagger) |
| 2     | `sale` — `SaleCalculator`, `SaleService.checkout()`, and its tests. The core of the app                            |
| 3     | `/auth/**` — Spring Security + JWT, then apply authorization to the earlier endpoints                              |
| 4     | `/stock/in` and `/stock/adjustment`                                                                                |
| 5     | `GET /sales`, `/reports/daily`, `/store-settings`                                                                  |

Once stage 3 is done, the frontend can drop the mock for the login, product, and cashier flows.

---

## 13. Notes for AI Assistants

- **Read first** `docs/api-spec.md` and PRD section 14 before creating a new entity or endpoint. Request/response shapes are already fixed; do not invent fields.
- **The API contract is already used by the frontend** (section 0). Changing a response shape is a breaking change — confirm first.
- **All packages are layered (section 2).** **Do not** add `domain/`, `port/`, `application/` layers, use cases, or a separate domain model in any package, including `sale`. The only separation allowed is the pure helper classes `SaleCalculator` and `InvoiceNumberGenerator`.
- **Work on one feature package at a time**; do not spread changes across many features in one step.
- **Always include tests** for new business logic, especially anything touching stock or money.
- **Do not modify existing Flyway migration files.** Create a new file.
- **Do not add new dependencies, frameworks, or architectural patterns** (event bus, CQRS, etc.) unless asked. This project is deliberately kept simple.
- **Do not build layered abstractions for simple CRUD.** No generic base service in this project.
- If the spec is ambiguous, **ask first**; do not assume and then make design decisions on your own.
- Code comments in English. User-facing error messages in Bahasa Indonesia.
