# ePOS API

Backend REST API untuk aplikasi kasir (POS) toko kelontong.
Kontrak API: [`docs/api-spec.md`](docs/api-spec.md) · PRD: [`docs/PRD-ePOS-Toko-Kelontong.md`](docs/PRD-ePOS-Toko-Kelontong.md) · Panduan proyek: [`AGENTS.md`](AGENTS.md)

## Tech stack

Java 21 · Spring Boot 3.3 · Maven · PostgreSQL 16 · Spring Data JPA · Flyway · Spring Security + JWT · springdoc-openapi · MapStruct · Lombok · Testcontainers

## Menjalankan

Prasyarat: JDK 21 dan Docker.

```bash
cp .env.example .env         # isi kredensial DB lokal (file ini tidak di-commit)
docker compose up -d db      # PostgreSQL di port 5432
./mvnw spring-boot:run       # API di port 8080, profil dev (default)
./mvnw test                  # seluruh test (butuh Docker untuk Testcontainers)
./mvnw verify                # test + build
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health
- Seluruh endpoint bisnis berada di bawah `/api` (misalnya `/api/products`).

Menjalankan API sebagai container (profil `prod`):

```bash
docker compose --profile app up -d --build
```

### Profil

| Profil | Kegunaan |
|---|---|
| `dev` | Default lokal. Membaca `.env`, menjalankan data seed (`db/seed/dev`). |
| `test` | Dipakai test; database disediakan Testcontainers. |
| `prod` | Semua konfigurasi dari environment variable, termasuk `EPOS_CORS_ALLOWED_ORIGINS`. |

### Data seed (profil `dev`)

| Username | Role | Password |
|---|---|---|
| `budi` | OWNER | `rahasia123` |
| `rina` | CASHIER | `rahasia123` |

Ditambah 4 kategori dan 8 produk contoh dari PRD poin 12.

## Arsitektur

Package-by-feature dengan **layered architecture** di setiap package:

```
Controller  →  Service  →  Repository  →  Database
   (DTO)       (logika)      (entity)
```

```
com.yoedi.epos
├── config/      SecurityConfig, OpenApiConfig, JacksonConfig, CorsConfig
├── common/      exception/, dto/PageResponse, entity/BaseEntity
├── auth/  user/  category/  product/  stock/  sale/  report/  store/
```

### Trade-off yang disadari: tanpa clean architecture

Proyek ini **sengaja tidak** memakai clean/hexagonal architecture (model domain terpisah, port/adapter, use case). Untuk aplikasi seukuran ini, lapisan tambahan sebagian besar hanya menghasilkan mapper dan boilerplate tanpa manfaat nyata. Konsekuensinya, entity JPA dipakai langsung di lapisan service.

Satu-satunya pengecualian: perhitungan murni penjualan (`SaleCalculator`, `InvoiceNumberGenerator`) dipisah ke kelas biasa tanpa Spring agar bisa diuji unit dengan cepat.

## Database

- Skema hanya diubah lewat Flyway (`src/main/resources/db/migration`). `ddl-auto=validate`.
- File migrasi yang sudah di-commit tidak boleh diedit — buat `V<n>__*.sql` baru.
- Data seed dev ada di `src/main/resources/db/seed/dev` dan hanya dimuat oleh profil `dev`.
