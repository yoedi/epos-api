# ePOS API — Panduan Proyek

Backend REST API untuk aplikasi kasir (POS) toko kelontong.

Frontend Angular berada di repo terpisah (`epos-web-ngmodule`, dan nantinya `epos-web` versi standalone) dan berkomunikasi **hanya** lewat REST API ini. Frontend sudah hampir selesai dibangun di atas kontrak di `docs/api-spec.md`.

Dokumen acuan: `docs/PRD-ePOS-Toko-Kelontong.md` (model data ada di poin 14) dan `docs/api-spec.md`.

---

## 0. Kontrak API sudah dipakai frontend

`docs/api-spec.md` bukan rancangan, melainkan kontrak yang **sudah diimplementasikan di sisi frontend**. Konsekuensinya:

- Nama field, bentuk response, kode HTTP, dan nilai `code` pada error **tidak boleh diubah** tanpa alasan kuat.
- Jika sebuah endpoint memang harus berubah, perbarui `docs/api-spec.md` di commit yang sama dan sebutkan perubahannya secara eksplisit di pesan commit, supaya sisi frontend bisa menyesuaikan.
- Jangan "memperbaiki" penamaan menjadi lebih rapi (misalnya `change_amount` → `change`) hanya karena terlihat lebih baik.

---

## 1. Tech Stack

| Bagian | Pilihan |
|---|---|
| Bahasa | Java 21 |
| Framework | Spring Boot 3.3.x |
| Build tool | Maven |
| Database | PostgreSQL 16 |
| ORM | Spring Data JPA (Hibernate) |
| Migrasi DB | Flyway |
| Keamanan | Spring Security + JWT (jjwt) |
| Validasi | Bean Validation (Jakarta) |
| Dokumentasi API | springdoc-openapi (Swagger UI) |
| Mapping DTO | MapStruct |
| Boilerplate | Lombok |
| Testing | JUnit 5, AssertJ, Testcontainers, MockMvc |
| Container | Docker + Docker Compose |

**Aturan dependency:** jangan menambah dependency baru tanpa alasan jelas. Utamakan yang sudah tersedia di Spring Boot.

---

## 2. Arsitektur

Seluruh package memakai **layered architecture** yang sama.

```
Controller  →  Service  →  Repository  →  Database
   (DTO)       (logika)      (entity)
```

Proyek ini **sengaja tidak** memakai clean architecture (model domain terpisah, port/adapter, use case). Untuk aplikasi seukuran ini, lapisan tambahan sebagian besar hanya menghasilkan mapper dan boilerplate. Catat keputusan ini di README sebagai trade-off yang disadari.

Aturan yang tidak boleh dilanggar:

1. **Controller tidak berisi logika bisnis.** Hanya menerima request, memvalidasi bentuknya, memanggil service, mengembalikan response.
2. **Entity JPA tidak pernah keluar dari lapisan service.** Controller selalu menerima dan mengembalikan DTO.
3. **Repository hanya dipanggil dari service**, tidak pernah langsung dari controller.
4. **Logika bisnis ada di service**, bukan di controller atau entity.
5. **Tidak ada siklus dependency antar service.** Jika butuh data dari feature lain, panggil service-nya, bukan repository-nya.

### Pengecualian kecil di `sale`

`SaleService` memuat aturan bisnis paling padat: validasi stok, hitung ulang total, cek pembayaran, penomoran invoice. Agar tetap bisa diuji cepat, **pisahkan perhitungan murninya** ke kelas helper tanpa dependency Spring di package `sale`:

- `SaleCalculator` — hitung subtotal per item, total, dan kembalian.
- `InvoiceNumberGenerator` — format `INV-YYYYMMDD-NNNN`.

Keduanya kelas biasa tanpa anotasi, diuji dengan unit test tanpa database. Urusan transaksi, locking, dan penyimpanan tetap di `SaleService`. Jangan memperluas ini menjadi lapisan domain atau use case.

---

## 3. Struktur Package

Package-by-feature, bukan package-by-layer.

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

## 4. Konvensi Penamaan

| Jenis | Pola | Contoh |
|---|---|---|
| Tabel & kolom DB | `snake_case`, tabel jamak | `sale_items`, `sell_price` |
| Entity JPA | `PascalCase` tunggal | `SaleItem` |
| Field Java | `camelCase` | `sellPrice` |
| JSON request/response | `snake_case` | `"sell_price": 36500` |
| DTO request | `<Aksi><Entity>Request` | `CreateProductRequest` |
| DTO response | `<Entity>Response` | `ProductResponse` |
| Method repository | `findBy...`, `existsBy...` | `findByBarcode` |
| File migrasi Flyway | `V<n>__deskripsi.sql` | `V1__init.sql` |
| Branch git | `feat/`, `fix/`, `chore/` | `feat/sale-transaction` |
| Commit | Conventional Commits | `feat(sale): add checkout endpoint` |

Konversi `camelCase` Java ke `snake_case` JSON diatur sekali lewat `PropertyNamingStrategies.SNAKE_CASE` di `JacksonConfig`, bukan dengan `@JsonProperty` di tiap field.

---

## 5. Aturan Data (wajib dipatuhi)

Ringkasan dari PRD poin 14.4:

- **Uang** disimpan sebagai `long` / `BIGINT` dalam rupiah, tanpa desimal. **Dilarang memakai `double` atau `float`.**
- **`products.stock` adalah cache.** Sumber kebenaran stok adalah tabel `stock_movements`. Keduanya diperbarui dalam transaksi yang sama.
- **Stok tidak boleh negatif**, dijaga dengan `CHECK (stock >= 0)` di DB dan validasi di domain.
- **`sale_items` menyimpan snapshot** `product_name`, `unit`, dan `price` saat transaksi.
- **Tidak ada hapus fisik** untuk produk dan user; gunakan `is_active`. Foreign key ke `products` dan `users` memakai `ON DELETE RESTRICT`.
- **Total transaksi selalu dihitung ulang di server.** Nilai total dari client tidak pernah dipercaya; client hanya mengirim `product_id` dan `qty`.
- **Waktu** disimpan sebagai `timestamptz` (`OffsetDateTime`). Laporan harian memakai zona `Asia/Jakarta`.
- **Nomor invoice** format `INV-YYYYMMDD-NNNN`, urutan diulang tiap hari.

---

## 6. Database & Migrasi

- Skema **hanya** diubah lewat file migrasi Flyway di `src/main/resources/db/migration/`.
- `spring.jpa.hibernate.ddl-auto=validate`. **Jangan pernah** memakai `update` atau `create`.
- File migrasi yang sudah di-commit **tidak boleh diedit** (Flyway menyimpan checksum). Perubahan dibuat sebagai file baru (`V2__...`, `V3__...`).
- Data awal untuk pengembangan — user `rina` (CASHIER) dan `budi` (OWNER), kategori, dan produk contoh dari PRD poin 12 — ditaruh di `V2__seed_dev_data.sql` dan hanya aktif di profil `dev`. Password seed sama dengan yang dipakai mock di frontend.

---

## 7. Transaksi Penjualan (bagian paling kritis)

`SaleService.checkout()` wajib mengikuti urutan ini di dalam satu `@Transactional`:

1. Ambil semua produk yang dibeli dengan **pessimistic write lock**
   (`@Lock(LockModeType.PESSIMISTIC_WRITE)` di repository, setara `SELECT ... FOR UPDATE`).
   **Urutkan pengambilan berdasarkan `product_id`** untuk menghindari deadlock saat dua transaksi membeli produk yang sama dengan urutan berbeda.
2. Validasi produk aktif dan stok mencukupi. Jika tidak, lempar `ProductInactiveException` atau `InsufficientStockException`.
3. Hitung `subtotal` dan `total` lewat `SaleCalculator`, memakai harga dari database — bukan dari request.
4. Validasi `paid >= total`; jika kurang, lempar `InsufficientPaymentException`. Hitung kembalian.
5. Generate `invoice_no` lewat `InvoiceNumberGenerator`.
6. Simpan `sales` dan `sale_items`, dengan snapshot `product_name`, `unit`, dan `price`.
7. Kurangi `products.stock` dan catat satu baris `stock_movements` bertipe `SALE` per item, lengkap dengan `stock_before`, `stock_after`, dan `sale_id`.

Exception apa pun akan me-rollback seluruh langkah, termasuk penomoran invoice. **Jangan** menangkap exception di tengah proses lalu melanjutkan.

---

## 8. Keamanan

- Autentikasi JWT stateless. Token berisi `sub` (user id), `username`, `role`, `exp`.
- Masa berlaku access token 8 jam (satu shift kerja). Refresh token belum ada di v1.
- Password di-hash dengan BCrypt (strength 10).
- Otorisasi dengan `@PreAuthorize("hasRole('OWNER')")`. Batasan per role ada di `docs/api-spec.md` bagian "Ringkasan hak akses".
- `CASHIER` pada `GET /sales` hanya menerima transaksinya sendiri hari ini, **apa pun filter yang dikirim** — difilter di server, bukan di frontend.
- Endpoint publik hanya `POST /api/auth/login`, Swagger UI, dan actuator health.
- **Jangan pernah** mengembalikan `password_hash`, atau `cost_price` untuk role `CASHIER`.
- Konfigurasi rahasia (JWT secret, kredensial DB) lewat environment variable, **tidak pernah** di-hardcode atau di-commit.
- **CORS:** saat pengembangan frontend memakai proxy Angular, jadi tidak perlu CORS. Untuk profil `prod`, atur origin yang diizinkan lewat environment variable di `CorsConfig` — jangan `allowedOrigins("*")` bersamaan dengan credentials.

---

## 9. Format Response & Error

Response sukses mengembalikan objek DTO langsung (tanpa pembungkus `{data: ...}`). Daftar bersambung memakai `PageResponse`.

Error memakai `ProblemDetail` (RFC 7807) yang ditangani terpusat di `GlobalExceptionHandler`:

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

Pemetaan kode HTTP:

| Situasi | Status |
|---|---|
| Validasi request gagal | 400 |
| Token tidak valid / tidak ada | 401 |
| Role tidak berwenang | 403 |
| Data tidak ditemukan | 404 |
| Konflik data (SKU/barcode duplikat, kategori masih dipakai) | 409 |
| Aturan bisnis dilanggar (stok kurang, uang kurang, produk nonaktif) | 422 |
| Error tak terduga | 500 |

- Field `code` **wajib ada di setiap error** dan nilainya harus persis seperti daftar di `docs/api-spec.md`. Frontend memakai nilai ini untuk menentukan cara menampilkan: inline di form, inline di keranjang, atau toast.
- Pesan `detail` ditulis dalam **Bahasa Indonesia** karena ditampilkan langsung ke pengguna.
- Error validasi menyertakan array `errors` berisi `{ field, message }`.
- Setiap exception domain baru di `sale` harus didaftarkan pemetaannya di `GlobalExceptionHandler`; jangan sampai bocor jadi 500.

---

## 10. Testing

Prioritas pengujian, bukan mengejar angka coverage:

1. **Unit test `SaleCalculator` dan `InvoiceNumberGenerator`** — tanpa Spring, tanpa database, selesai dalam milidetik. Wajib mencakup: hitung subtotal dan total, kembalian, dan format nomor invoice termasuk pergantian hari.
2. **Integration test `SaleService.checkout()`** memakai Testcontainers PostgreSQL. Wajib mencakup: transaksi normal, keranjang kosong, stok tidak cukup, uang kurang, produk nonaktif, dan **dua transaksi bersamaan atas produk yang sama** (membuktikan pessimistic lock bekerja).
3. **MockMvc test** untuk controller: kode status, bentuk response (termasuk `code` pada error), dan aturan otorisasi per role.

Aturan: **jangan memakai H2** sebagai pengganti PostgreSQL dalam test, karena perilaku `SELECT ... FOR UPDATE` dan tipe datanya berbeda dari produksi.

---

## 11. Menjalankan Proyek

```bash
docker compose up -d db      # PostgreSQL di port 5432
./mvnw spring-boot:run       # API di port 8080, profil dev
./mvnw test                  # seluruh test
./mvnw verify                # test + build
```

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Profil: `dev` (default lokal), `test` (Testcontainers), `prod`.
- Frontend berjalan di port 4200 dan mem-proxy `/api/*` ke port 8080.

---

## 12. Urutan Implementasi

Mengikuti `docs/api-spec.md` bagian 9:

| Tahap | Isi |
|---|---|
| 1 | Setup proyek, `V1__init.sql`, entity, CRUD `/categories` dan `/products` (tanpa security dulu, uji di Swagger) |
| 2 | `sale` — `SaleCalculator`, `SaleService.checkout()`, dan test-nya. Inti aplikasi |
| 3 | `/auth/**` — Spring Security + JWT, lalu pasang otorisasi di endpoint sebelumnya |
| 4 | `/stock/in` dan `/stock/adjustment` |
| 5 | `GET /sales`, `/reports/daily`, `/store-settings` |

Begitu tahap 3 selesai, frontend sudah bisa dilepas dari mock untuk alur login, produk, dan kasir.

---

## 13. Catatan untuk Asisten AI

- **Baca dulu** `docs/api-spec.md` dan PRD poin 14 sebelum membuat entity atau endpoint baru. Bentuk request/response sudah ditetapkan; jangan mengarang field.
- **Kontrak API sudah dipakai frontend** (bagian 0). Perubahan bentuk response adalah perubahan yang merusak — konfirmasi dulu.
- **Seluruh package layered (bagian 2).** **Jangan** menambahkan lapisan `domain/`, `port/`, `application/`, use case, atau model domain terpisah di package mana pun, termasuk `sale`. Satu-satunya pemisahan yang diizinkan adalah kelas helper murni `SaleCalculator` dan `InvoiceNumberGenerator`.
- **Kerjakan satu feature package sekaligus**, jangan menyebar perubahan ke banyak fitur dalam satu langkah.
- **Selalu sertakan test** untuk logika bisnis baru, terutama yang menyentuh stok atau uang.
- **Jangan mengubah file migrasi Flyway yang sudah ada.** Buat file baru.
- **Jangan menambah dependency, framework, atau pola arsitektur baru** (event bus, CQRS, dll.) tanpa diminta. Proyek ini sengaja dijaga sederhana.
- **Jangan membuat abstraksi berlapis untuk CRUD sederhana.** Generic base service tidak dipakai di proyek ini.
- Jika spesifikasi ambigu, **tanyakan dulu**, jangan berasumsi lalu membuat keputusan desain sendiri.
- Komentar kode dalam Bahasa Inggris. Pesan error untuk pengguna dalam Bahasa Indonesia.
