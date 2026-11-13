# API Specification — ePOS v1

**Base URL:** `http://localhost:8080/api`
**Format:** JSON, `snake_case` field naming
**Authentication:** Bearer JWT in the `Authorization` header, except the login endpoint
**References:** PRD section 9 (screens), section 14 (data model), `AGENTS.md` (conventions)

> Note on language: this spec is in English, but the `title`, `detail`, and validation `message` values in error examples are kept in Bahasa Indonesia because the frontend shows them directly to users. Stored sample values (such as notes) are also kept as-is.

---

## General Conventions

### Money format

All monetary values are whole rupiah integers. `36500` means Rp 36.500. No decimals.

### Time format

ISO-8601 with offset: `"2026-09-16T14:32:10+07:00"`.

### Pagination

Query params: `page` (starts at 0, default 0), `size` (default 20, max 100), `sort` (example `name,asc`).

`sort` uses the same `snake_case` names as the response fields (for example `created_at,desc`, `sell_price,asc`, `is_active,desc`). A name the endpoint does not support, including a camelCase one, returns 400 `VALIDATION_ERROR`.

Shape of a paginated list response:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "total_elements": 137,
  "total_pages": 7,
  "last": false
}
```

### Error format

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

Validation errors include a list of fields:

```json
{
  "title": "Validasi gagal",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "errors": [
    { "field": "sell_price", "message": "Harga jual wajib diisi" },
    { "field": "sku", "message": "SKU wajib diisi" }
  ]
}
```

### Error code list

| `code`                 | Status | Meaning                                 |
| ---------------------- | ------ | --------------------------------------- |
| `VALIDATION_ERROR`     | 400    | Request does not meet validation rules  |
| `UNAUTHORIZED`         | 401    | Token missing, expired, or invalid      |
| `INVALID_CREDENTIALS`  | 401    | Wrong username or password              |
| `FORBIDDEN`            | 403    | Role not authorized                     |
| `NOT_FOUND`            | 404    | Data not found                          |
| `DUPLICATE_SKU`        | 409    | SKU already used by another product     |
| `DUPLICATE_BARCODE`    | 409    | Barcode already used by another product |
| `DUPLICATE_CATEGORY`   | 409    | Category name already exists            |
| `CATEGORY_IN_USE`      | 409    | Category still has products             |
| `INSUFFICIENT_STOCK`   | 422    | Product stock is insufficient           |
| `INSUFFICIENT_PAYMENT` | 422    | Amount received is less than the total  |
| `PRODUCT_INACTIVE`     | 422    | Inactive products cannot be sold        |
| `EMPTY_CART`           | 422    | Transaction with no items               |
| `NO_STOCK_DIFFERENCE`  | 422    | Stock adjustment with no difference     |
| `INTERNAL_ERROR`       | 500    | Unexpected error                        |

### Access rights summary

| Endpoint                                        | OWNER    | CASHIER                  |
| ----------------------------------------------- | -------- | ------------------------ |
| `POST /auth/login`                              | public   | public                   |
| `GET /products`, `GET /products/barcode/{code}` | ✓        | ✓ (without `cost_price`) |
| `POST/PATCH /products`, `/categories`           | ✓        | —                        |
| `POST /sales`                                   | ✓        | ✓                        |
| `GET /sales`                                    | all data | own only, today only     |
| `GET /users`                                    | ✓        | —                        |
| `POST /stock/in`, `/stock/adjustment`           | ✓        | —                        |
| `GET /reports/**`                               | ✓        | —                        |
| `GET/PUT /store-settings`                       | ✓        | GET only                 |

---

## 1. Auth

### POST /auth/login

Public. Screen 9.1.

**Request**

```json
{ "username": "rina", "password": "rahasia123" }
```

**Response 200**

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "token_type": "Bearer",
  "expires_in": 28800,
  "user": {
    "id": 2,
    "name": "Rina Lestari",
    "username": "rina",
    "role": "CASHIER"
  }
}
```

**Error:** 401 `INVALID_CREDENTIALS` (also used when the user is inactive, so account status is not leaked).

### GET /auth/me

Returns the user data from the token. Used by the frontend when reloading the page.

**Response 200:** a `user` object as above.

---

## 2. Categories

### GET /categories

Screen 9.6 and the filter in 9.5. Not paginated (the number of categories is small).

**Response 200**

```json
[
  { "id": 1, "name": "Sembako", "product_count": 4 },
  { "id": 2, "name": "Minuman", "product_count": 2 }
]
```

### POST /categories — OWNER

**Request:** `{ "name": "Kebutuhan Rumah" }`
**Response 201:** category object.
**Error:** 409 `DUPLICATE_CATEGORY`.

### PATCH /categories/{id} — OWNER

**Request:** `{ "name": "Kebutuhan Rumah Tangga" }`
**Response 200:** category object.

### DELETE /categories/{id} — OWNER

**Response 204.**
**Error:** 409 `CATEGORY_IN_USE` — detail: "Kategori Sembako masih memiliki 4 produk".

---

## 3. Products

### GET /products

Screen 9.5 and search in 9.2.

**Query params**

| Param                  | Type   | Description                                      |
| ---------------------- | ------ | ------------------------------------------------ |
| `search`               | string | Matches name, SKU, or barcode (case-insensitive) |
| `category_id`          | long   | Category filter                                  |
| `status`               | enum   | `ACTIVE`, `INACTIVE`, `LOW_STOCK`                |
| `page`, `size`, `sort` |        | Pagination                                       |

**Response 200** (wrapped in `PageResponse`)

```json
{
  "id": 2,
  "sku": "MYK-002",
  "barcode": "8991234567890",
  "name": "Minyak Goreng 2 L",
  "category": { "id": 1, "name": "Sembako" },
  "unit": "LITER",
  "cost_price": 32000,
  "sell_price": 36500,
  "stock": 3,
  "min_stock": 12,
  "stock_status": "LOW_STOCK",
  "is_active": true
}
```

`stock_status` is computed by the server: `OUT_OF_STOCK` when stock is 0, `LOW_STOCK` when `stock <= min_stock`, otherwise `SAFE`. For the `CASHIER` role, the `cost_price` field is omitted.

### GET /products/barcode/{code}

Used when scanning a barcode on the cashier screen.

**Response 200:** product object.
**Error:** 404 `NOT_FOUND` — detail: "Barang dengan barcode 8991234567890 tidak ditemukan".

### GET /products/{id}

**Response 200:** product object.

### POST /products — OWNER

**Request**

```json
{
  "sku": "KCP-001",
  "barcode": "8991111222333",
  "name": "Kecap Manis 275 ml",
  "category_id": 1,
  "unit": "PCS",
  "cost_price": 9500,
  "sell_price": 11500,
  "initial_stock": 24,
  "min_stock": 10
}
```

Rules: `sku` is required and unique; `barcode` is optional, unique if provided; prices `>= 0`; `sell_price < cost_price` is allowed but the frontend shows a warning. If `initial_stock > 0`, the system automatically creates one `stock_movement` of type `IN` with the note "Stok awal".

**Response 201:** product object.
**Error:** 409 `DUPLICATE_SKU` / `DUPLICATE_BARCODE`, 404 if the category does not exist.

### PATCH /products/{id} — OWNER

Partial update; only the fields sent are changed. **`stock` cannot be changed through this endpoint**, use the stock endpoints so history is recorded.

**Request:** `{ "sell_price": 37000, "min_stock": 15 }`
**Response 200:** product object.

### PATCH /products/{id}/status — OWNER

**Request:** `{ "is_active": false }`
**Response 200:** product object.

---

## 4. Sales

### POST /sales

The most critical endpoint. Screen 9.2. Used by OWNER and CASHIER.

**Request**

```json
{
  "items": [
    { "product_id": 1, "qty": 1 },
    { "product_id": 5, "qty": 5 },
    { "product_id": 4, "qty": 1 },
    { "product_id": 8, "qty": 10 }
  ],
  "payment_method": "CASH",
  "paid": 150000
}
```

The client **does not send prices or totals**. The server recalculates from the database.

**Response 201**

```json
{
  "id": 1,
  "invoice_no": "INV-20260916-0001",
  "cashier": { "id": 2, "name": "Rina Lestari" },
  "items": [
    {
      "product_id": 1,
      "product_name": "Beras Premium 5 kg",
      "unit": "KG",
      "price": 78000,
      "qty": 1,
      "subtotal": 78000
    }
  ],
  "subtotal": 144500,
  "discount": 0,
  "total": 144500,
  "payment_method": "CASH",
  "paid": 150000,
  "change_amount": 5500,
  "status": "COMPLETED",
  "created_at": "2026-09-16T14:32:10+07:00"
}
```

**Errors**

| Condition            | Status | `code`                 | Example `detail`                       |
| -------------------- | ------ | ---------------------- | -------------------------------------- |
| `items` empty        | 422    | `EMPTY_CART`           | "Keranjang masih kosong"               |
| Insufficient stock   | 422    | `INSUFFICIENT_STOCK`   | "Stok Minyak Goreng 2 L tersisa 3"     |
| Insufficient payment | 422    | `INSUFFICIENT_PAYMENT` | "Uang kurang Rp 44.500"                |
| Inactive product     | 422    | `PRODUCT_INACTIVE`     | "Produk Kopi Sachet sudah tidak aktif" |
| Product not found    | 404    | `NOT_FOUND`            | "Produk tidak ditemukan"               |

Transactional behavior is defined in `AGENTS.md` section 7. All steps run in a single transaction; any failure cancels everything, including invoice numbering.

### GET /sales

Screen 9.9.

**Query params:** `start_date`, `end_date` (format `YYYY-MM-DD`, default today), `cashier_id` (OWNER only), `invoice_no`, plus pagination.

A CASHIER receives only their own transactions for today, regardless of any filter sent.

**Response 200:** `PageResponse` containing transaction summaries, plus aggregates according to the filter (across all filtered results, not just this page):

```json
{
  "content": [
    {
      "id": 1,
      "invoice_no": "INV-20260916-0001",
      "cashier_name": "Rina Lestari",
      "item_count": 4,
      "total": 144500,
      "created_at": "2026-09-16T14:32:10+07:00"
    }
  ],
  "summary": {
    "total_revenue": 4305000,
    "transaction_count": 42,
    "average_transaction": 102500
  },
  "page": 0,
  "size": 20,
  "total_elements": 42,
  "total_pages": 3,
  "last": false
}
```

`item_count` is the sum of qty across all items (not the number of lines). `average_transaction` is computed by the server the same way as in `/reports/daily` (whole rupiah integer, `0` if there are no transactions); the frontend does not recompute it.

### GET /sales/{id}

Transaction detail for the drawer and receipt reprint.

**Response 200:** same object as the `POST /sales` response, plus `store` (store name, address, phone, receipt footer) so the receipt can be printed from a single call.

---

## 5. Stock

### POST /stock/in — OWNER

Screen 9.7. Supports several items in one save.

**Request**

```json
{
  "note": "Belanja grosir 16 Sep",
  "items": [
    { "product_id": 2, "qty": 24 },
    { "product_id": 6, "qty": 48 }
  ]
}
```

**Response 201**

```json
{
  "movements": [
    {
      "id": 31,
      "product_id": 2,
      "product_name": "Minyak Goreng 2 L",
      "type": "IN",
      "qty": 24,
      "stock_before": 3,
      "stock_after": 27,
      "note": "Belanja grosir 16 Sep",
      "created_at": "2026-09-16T15:02:00+07:00"
    }
  ]
}
```

Rules: `qty` must be `> 0`. All items are saved in a single transaction.

### POST /stock/adjustment — OWNER

Screen 9.8. One product per request.

**Request**

```json
{
  "product_id": 2,
  "physical_stock": 26,
  "reason": "RUSAK",
  "note": "Kemasan bocor 1 botol"
}
```

The client sends the **physical count result**, not the difference. The server computes `qty = physical_stock - current_stock`. If the difference is 0, return 422 with code `NO_STOCK_DIFFERENCE` and the detail "Tidak ada selisih stok".

**Response 201:** movement object as above, with `type: "ADJUSTMENT"` and `reason` filled in.

**`reason` values:** `RUSAK`, `KEDALUWARSA`, `HILANG`, `SALAH_HITUNG`, `LAINNYA`.

### GET /stock/movements — OWNER

Stock movement history. Query params: `product_id`, `type`, `start_date`, `end_date`, plus pagination. Sorted by `created_at` descending.

---

## 6. Reports

### GET /reports/daily — OWNER

Screen 9.4. Query param `date` (default today, Asia/Jakarta zone).

**Response 200**

```json
{
  "date": "2026-09-16",
  "total_revenue": 1875000,
  "transaction_count": 23,
  "average_transaction": 81521,
  "low_stock_count": 3,
  "hourly_sales": [
    { "hour": 8, "total": 125000, "transaction_count": 2 },
    { "hour": 9, "total": 340000, "transaction_count": 5 }
  ],
  "top_products": [
    {
      "product_id": 5,
      "sku": "MIE-001",
      "product_name": "Mi Instan Goreng",
      "qty_sold": 45,
      "total": 157500
    }
  ],
  "low_stock_products": [
    {
      "product_id": 2,
      "sku": "MYK-002",
      "product_name": "Minyak Goreng 2 L",
      "stock": 3,
      "min_stock": 12,
      "stock_status": "LOW_STOCK"
    }
  ]
}
```

`hourly_sales` contains only hours that have transactions; the frontend fills empty hours with 0. `top_products` is limited to the top 5, sorted by `total` descending. `sku` in `top_products` and `low_stock_products` is the product's current SKU (shown on the Dashboard, mockup 3a).

---

## 7. Store Settings

### GET /store-settings

Used by the app header and receipts. Accessible by all roles.

**Response 200**

```json
{
  "store_name": "Toko Berkah Jaya",
  "address": "Jl. Kaliurang Km 5, Yogyakarta",
  "phone": "0274-123456",
  "receipt_footer": "Terima kasih, selamat belanja kembali"
}
```

### PUT /store-settings — OWNER

**Request:** same object as the response.
**Response 200:** the updated object.

**Validation (400 `VALIDATION_ERROR`):**

- `store_name` is required, max 100 characters.
- `address` max 255, `phone` max 30, `receipt_footer` max 255 characters.
- `address`, `phone`, and `receipt_footer` are optional. A missing, empty, or whitespace-only value is stored as `null` and returned as `null`.
- Leading and trailing whitespace is trimmed.

The store has exactly one settings record, so there is no `id` in the request or response and no create or delete endpoint.

---

## 8. Users

### GET /users — OWNER

List of users for the cashier filter in screen 9.9. Not paginated (the number of users is small). Sorted by `name` ascending.

**Query params:** `role` (`OWNER` | `CASHIER`, optional).

**Response 200**

```json
[
  {
    "id": 3,
    "name": "Dewi Anggraini",
    "username": "dewi",
    "role": "CASHIER",
    "is_active": true
  },
  {
    "id": 2,
    "name": "Rina Lestari",
    "username": "rina",
    "role": "CASHIER",
    "is_active": true
  }
]
```

Inactive users are still returned so their past transactions can still be filtered.

---

## 9. Endpoint Summary

| Method | Path                       | Role   | Screen   |
| ------ | -------------------------- | ------ | -------- |
| POST   | `/auth/login`              | public | 9.1      |
| GET    | `/auth/me`                 | all    | —        |
| GET    | `/categories`              | all    | 9.5, 9.6 |
| POST   | `/categories`              | OWNER  | 9.6      |
| PATCH  | `/categories/{id}`         | OWNER  | 9.6      |
| DELETE | `/categories/{id}`         | OWNER  | 9.6      |
| GET    | `/products`                | all    | 9.2, 9.5 |
| GET    | `/products/{id}`           | all    | 9.5      |
| GET    | `/products/barcode/{code}` | all    | 9.2      |
| POST   | `/products`                | OWNER  | 9.5      |
| PATCH  | `/products/{id}`           | OWNER  | 9.5      |
| PATCH  | `/products/{id}/status`    | OWNER  | 9.5      |
| POST   | `/sales`                   | all    | 9.2      |
| GET    | `/sales`                   | all\*  | 9.9      |
| GET    | `/sales/{id}`              | all\*  | 9.3, 9.9 |
| POST   | `/stock/in`                | OWNER  | 9.7      |
| POST   | `/stock/adjustment`        | OWNER  | 9.8      |
| GET    | `/stock/movements`         | OWNER  | 9.7      |
| GET    | `/reports/daily`           | OWNER  | 9.4      |
| GET    | `/store-settings`          | all    | 9.3      |
| PUT    | `/store-settings`          | OWNER  | —        |
| GET    | `/users`                   | OWNER  | 9.9      |

\* CASHIER is restricted to their own transactions for today.

---

## 10. Implementation Order

| Stage | Endpoints                                                     | Notes                                                             |
| ----- | ------------------------------------------------------------- | ----------------------------------------------------------------- |
| 1     | `/categories`, `/products`                                    | Basic CRUD, no security yet                                       |
| 2     | `/sales`                                                      | Core of the app, build together with integration tests            |
| 3     | `/auth/**`                                                    | Security + JWT, then apply authorization to the earlier endpoints |
| 4     | `/stock/**`                                                   | Incoming stock and adjustment                                     |
| 5     | `/sales` (GET), `/users`, `/reports/daily`, `/store-settings` | History and reports                                               |

---

## 11. Planned Roadmap Changes

Prepared from v1 so the contract does not need reworking:

- **v2:** per-item `discount` in `POST /sales`; `POST /sales/{id}/void`; `POST /sales/{id}/return`; `stock_movements.type` gains `RETURN` and `VOID`.
- **v3:** `payment_method` gains `QRIS` and others; member and points endpoints; `GET /reports/daily/export`.
- **v4:** `store_id` on all main entities; sync endpoints for offline mode.
