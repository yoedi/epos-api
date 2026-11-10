# Spesifikasi API — ePOS v1

**Base URL:** `http://localhost:8080/api`
**Format:** JSON, penamaan field `snake_case`
**Autentikasi:** Bearer JWT di header `Authorization`, kecuali endpoint login
**Acuan:** PRD poin 9 (layar), poin 14 (model data), `CLAUDE.md` (konvensi)

---

## Konvensi Umum

### Format uang

Seluruh nilai uang berupa bilangan bulat rupiah. `36500` berarti Rp 36.500. Tidak ada desimal.

### Format waktu

ISO-8601 dengan offset: `"2026-09-16T14:32:10+07:00"`.

### Paginasi

Query param: `page` (mulai dari 0, default 0), `size` (default 20, maksimal 100), `sort` (contoh `name,asc`).

Bentuk response daftar bersambung:

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

### Format error

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

Error validasi menyertakan daftar field:

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

### Daftar kode error

| `code`                 | Status | Arti                                           |
| ---------------------- | ------ | ---------------------------------------------- |
| `VALIDATION_ERROR`     | 400    | Request tidak memenuhi aturan validasi         |
| `UNAUTHORIZED`         | 401    | Token tidak ada, kedaluwarsa, atau tidak valid |
| `INVALID_CREDENTIALS`  | 401    | Username atau password salah                   |
| `FORBIDDEN`            | 403    | Role tidak berwenang                           |
| `NOT_FOUND`            | 404    | Data tidak ditemukan                           |
| `DUPLICATE_SKU`        | 409    | SKU sudah dipakai produk lain                  |
| `DUPLICATE_BARCODE`    | 409    | Barcode sudah dipakai produk lain              |
| `DUPLICATE_CATEGORY`   | 409    | Nama kategori sudah ada                        |
| `CATEGORY_IN_USE`      | 409    | Kategori masih memiliki produk                 |
| `INSUFFICIENT_STOCK`   | 422    | Stok produk tidak mencukupi                    |
| `INSUFFICIENT_PAYMENT` | 422    | Uang diterima kurang dari total                |
| `PRODUCT_INACTIVE`     | 422    | Produk nonaktif tidak bisa dijual              |
| `EMPTY_CART`           | 422    | Transaksi tanpa item                           |
| `INTERNAL_ERROR`       | 500    | Kesalahan tak terduga                          |

### Ringkasan hak akses

| Endpoint                                        | OWNER      | CASHIER                       |
| ----------------------------------------------- | ---------- | ----------------------------- |
| `POST /auth/login`                              | publik     | publik                        |
| `GET /products`, `GET /products/barcode/{code}` | ✓          | ✓ (tanpa `cost_price`)        |
| `POST/PATCH /products`, `/categories`           | ✓          | —                             |
| `POST /sales`                                   | ✓          | ✓                             |
| `GET /sales`                                    | semua data | hanya milik sendiri, hari ini |
| `GET /users`                                    | ✓          | —                             |
| `POST /stock/in`, `/stock/adjustment`           | ✓          | —                             |
| `GET /reports/**`                               | ✓          | —                             |
| `GET/PUT /store-settings`                       | ✓          | GET saja                      |

---

## 1. Auth

### POST /auth/login

Publik. Layar 9.1.

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

**Error:** 401 `INVALID_CREDENTIALS` (juga dipakai saat user nonaktif, agar tidak membocorkan status akun).

### GET /auth/me

Mengembalikan data user dari token. Dipakai frontend saat memuat ulang halaman.

**Response 200:** objek `user` seperti di atas.

---

## 2. Categories

### GET /categories

Layar 9.6 dan filter di 9.5. Tidak dipaginasi (jumlah kategori sedikit).

**Response 200**

```json
[
  { "id": 1, "name": "Sembako", "product_count": 4 },
  { "id": 2, "name": "Minuman", "product_count": 2 }
]
```

### POST /categories — OWNER

**Request:** `{ "name": "Kebutuhan Rumah" }`
**Response 201:** objek kategori.
**Error:** 409 `DUPLICATE_CATEGORY`.

### PATCH /categories/{id} — OWNER

**Request:** `{ "name": "Kebutuhan Rumah Tangga" }`
**Response 200:** objek kategori.

### DELETE /categories/{id} — OWNER

**Response 204.**
**Error:** 409 `CATEGORY_IN_USE` — detail: "Kategori Sembako masih memiliki 4 produk".

---

## 3. Products

### GET /products

Layar 9.5 dan pencarian di 9.2.

**Query param**

| Param                  | Tipe   | Keterangan                                                 |
| ---------------------- | ------ | ---------------------------------------------------------- |
| `search`               | string | Cocokkan dengan nama, SKU, atau barcode (case-insensitive) |
| `category_id`          | long   | Filter kategori                                            |
| `status`               | enum   | `ACTIVE`, `INACTIVE`, `LOW_STOCK`                          |
| `page`, `size`, `sort` |        | Paginasi                                                   |

**Response 200** (dibungkus `PageResponse`)

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

`stock_status` dihitung server: `OUT_OF_STOCK` bila stok 0, `LOW_STOCK` bila `stock <= min_stock`, selain itu `SAFE`. Untuk role `CASHIER`, field `cost_price` dihilangkan.

### GET /products/barcode/{code}

Dipakai saat scan barcode di layar kasir.

**Response 200:** objek produk.
**Error:** 404 `NOT_FOUND` — detail: "Barang dengan barcode 8991234567890 tidak ditemukan".

### GET /products/{id}

**Response 200:** objek produk.

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

Aturan: `sku` wajib dan unik; `barcode` opsional, unik bila diisi; harga `>= 0`; `sell_price < cost_price` diperbolehkan tetapi frontend menampilkan peringatan. Jika `initial_stock > 0`, sistem otomatis membuat satu `stock_movement` bertipe `IN` dengan catatan "Stok awal".

**Response 201:** objek produk.
**Error:** 409 `DUPLICATE_SKU` / `DUPLICATE_BARCODE`, 404 kategori tidak ada.

### PATCH /products/{id} — OWNER

Partial update; hanya field yang dikirim yang diubah. **`stock` tidak bisa diubah lewat endpoint ini**, gunakan endpoint stok agar riwayat tercatat.

**Request:** `{ "sell_price": 37000, "min_stock": 15 }`
**Response 200:** objek produk.

### PATCH /products/{id}/status — OWNER

**Request:** `{ "is_active": false }`
**Response 200:** objek produk.

---

## 4. Sales

### POST /sales

Endpoint paling kritis. Layar 9.2. Dipakai OWNER dan CASHIER.

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

Client **tidak mengirim harga maupun total**. Server menghitung ulang dari database.

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

**Error**

| Kondisi          | Status | `code`                 | Contoh `detail`                        |
| ---------------- | ------ | ---------------------- | -------------------------------------- |
| `items` kosong   | 422    | `EMPTY_CART`           | "Keranjang masih kosong"               |
| Stok kurang      | 422    | `INSUFFICIENT_STOCK`   | "Stok Minyak Goreng 2 L tersisa 3"     |
| Uang kurang      | 422    | `INSUFFICIENT_PAYMENT` | "Uang kurang Rp 44.500"                |
| Produk nonaktif  | 422    | `PRODUCT_INACTIVE`     | "Produk Kopi Sachet sudah tidak aktif" |
| Produk tidak ada | 404    | `NOT_FOUND`            | "Produk tidak ditemukan"               |

Perilaku transaksional diatur di `CLAUDE.md` bagian 7. Seluruh langkah berjalan dalam satu transaksi; kegagalan apa pun membatalkan semuanya, termasuk penomoran invoice.

### GET /sales

Layar 9.9.

**Query param:** `start_date`, `end_date` (format `YYYY-MM-DD`, default hari ini), `cashier_id` (OWNER saja), `invoice_no`, plus paginasi.

CASHIER hanya menerima transaksinya sendiri hari ini, apa pun filter yang dikirim.

**Response 200:** `PageResponse` berisi ringkasan transaksi, ditambah agregat sesuai filter (seluruh hasil filter, bukan hanya halaman ini):

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
  "summary": { "total_revenue": 4305000, "transaction_count": 42, "average_transaction": 102500 },
  "page": 0,
  "size": 20,
  "total_elements": 42,
  "total_pages": 3,
  "last": false
}
```

`item_count` adalah jumlah qty seluruh item (bukan jumlah baris). `average_transaction` dihitung server dengan cara yang sama seperti di `/reports/daily` (bilangan bulat rupiah, `0` bila tidak ada transaksi); frontend tidak menghitung ulang.

### GET /sales/{id}

Detail transaksi untuk drawer dan cetak ulang struk.

**Response 200:** objek sama seperti response `POST /sales`, ditambah `store` (nama toko, alamat, telepon, footer struk) agar struk bisa dicetak dari satu panggilan.

---

## 5. Stock

### POST /stock/in — OWNER

Layar 9.7. Mendukung beberapa barang dalam satu kali simpan.

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

Aturan: `qty` wajib `> 0`. Seluruh item disimpan dalam satu transaksi.

### POST /stock/adjustment — OWNER

Layar 9.8. Satu produk per permintaan.

**Request**

```json
{
  "product_id": 2,
  "physical_stock": 26,
  "reason": "RUSAK",
  "note": "Kemasan bocor 1 botol"
}
```

Client mengirim **hasil hitung fisik**, bukan selisih. Server menghitung `qty = physical_stock - current_stock`. Jika selisih 0, kembalikan 422 dengan detail "Tidak ada selisih stok".

**Response 201:** objek movement seperti di atas, dengan `type: "ADJUSTMENT"` dan `reason` terisi.

**Nilai `reason`:** `RUSAK`, `KEDALUWARSA`, `HILANG`, `SALAH_HITUNG`, `LAINNYA`.

### GET /stock/movements — OWNER

Riwayat pergerakan stok. Query param: `product_id`, `type`, `start_date`, `end_date`, plus paginasi. Diurutkan `created_at` menurun.

---

## 6. Reports

### GET /reports/daily — OWNER

Layar 9.4. Query param `date` (default hari ini, zona Asia/Jakarta).

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
    { "product_id": 5, "sku": "MIE-001", "product_name": "Mi Instan Goreng", "qty_sold": 45, "total": 157500 }
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

`hourly_sales` hanya berisi jam yang memiliki transaksi; frontend melengkapi jam kosong dengan nilai 0. `top_products` dibatasi 5 teratas, diurutkan dari `total` terbesar. `sku` di `top_products` dan `low_stock_products` adalah SKU produk saat ini (ditampilkan di Dashboard, mockup 3a).

---

## 7. Store Settings

### GET /store-settings

Dipakai header aplikasi dan struk. Dapat diakses semua role.

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

**Request:** objek sama seperti response.
**Response 200:** objek yang sudah diperbarui.

---

## 8. Users

### GET /users — OWNER

Daftar pengguna untuk filter kasir di layar 9.9. Tidak dipaginasi (jumlah pengguna sedikit). Diurutkan `name` menaik.

**Query param:** `role` (`OWNER` | `CASHIER`, opsional).

**Response 200**

```json
[
  { "id": 3, "name": "Dewi Anggraini", "username": "dewi", "role": "CASHIER", "is_active": true },
  { "id": 2, "name": "Rina Lestari", "username": "rina", "role": "CASHIER", "is_active": true }
]
```

Pengguna nonaktif tetap dikembalikan agar transaksi lamanya tetap bisa difilter.

---

## 9. Ringkasan Endpoint

| Method | Path                       | Role    | Layar    |
| ------ | -------------------------- | ------- | -------- |
| POST   | `/auth/login`              | publik  | 9.1      |
| GET    | `/auth/me`                 | semua   | —        |
| GET    | `/categories`              | semua   | 9.5, 9.6 |
| POST   | `/categories`              | OWNER   | 9.6      |
| PATCH  | `/categories/{id}`         | OWNER   | 9.6      |
| DELETE | `/categories/{id}`         | OWNER   | 9.6      |
| GET    | `/products`                | semua   | 9.2, 9.5 |
| GET    | `/products/{id}`           | semua   | 9.5      |
| GET    | `/products/barcode/{code}` | semua   | 9.2      |
| POST   | `/products`                | OWNER   | 9.5      |
| PATCH  | `/products/{id}`           | OWNER   | 9.5      |
| PATCH  | `/products/{id}/status`    | OWNER   | 9.5      |
| POST   | `/sales`                   | semua   | 9.2      |
| GET    | `/sales`                   | semua\* | 9.9      |
| GET    | `/sales/{id}`              | semua\* | 9.3, 9.9 |
| POST   | `/stock/in`                | OWNER   | 9.7      |
| POST   | `/stock/adjustment`        | OWNER   | 9.8      |
| GET    | `/stock/movements`         | OWNER   | 9.7      |
| GET    | `/reports/daily`           | OWNER   | 9.4      |
| GET    | `/store-settings`          | semua   | 9.3      |
| PUT    | `/store-settings`          | OWNER   | —        |
| GET    | `/users`                   | OWNER   | 9.9      |

\* CASHIER dibatasi pada transaksinya sendiri hari ini.

---

## 10. Urutan Implementasi

| Tahap | Endpoint                                            | Catatan                                                      |
| ----- | --------------------------------------------------- | ------------------------------------------------------------ |
| 1     | `/categories`, `/products`                          | CRUD dasar, tanpa security dulu                              |
| 2     | `/sales`                                            | Inti aplikasi, kerjakan bersama integration test             |
| 3     | `/auth/**`                                          | Security + JWT, lalu pasang otorisasi di endpoint sebelumnya |
| 4     | `/stock/**`                                         | Stok masuk dan penyesuaian                                   |
| 5     | `/sales` (GET), `/users`, `/reports/daily`, `/store-settings` | Riwayat dan laporan                                          |

---

## 11. Rencana Perubahan di Roadmap

Disiapkan sejak v1 agar tidak merombak kontrak:

- **v2:** `discount` per item di `POST /sales`; `POST /sales/{id}/void`; `POST /sales/{id}/return`; `stock_movements.type` bertambah `RETURN` dan `VOID`.
- **v3:** `payment_method` bertambah `QRIS` dan lainnya; endpoint member dan poin; `GET /reports/daily/export`.
- **v4:** `store_id` pada seluruh entity utama; endpoint sinkronisasi untuk mode offline.
