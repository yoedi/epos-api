-- Development seed data (PRD section 12). Only loaded in the 'dev' profile
-- via spring.flyway.locations in application-dev.yml.
-- Password for every seed user: rahasia123 (BCrypt, strength 10).

INSERT INTO users (name, username, password_hash, role) VALUES
    ('Budi Santoso', 'budi', '$2a$10$IdDlNW4EPn8C7m2DL3h2tetEZNY0TGZMMgkBZGIsyLfpsP01dmZAm', 'OWNER'),
    ('Rina Lestari', 'rina', '$2a$10$IdDlNW4EPn8C7m2DL3h2tetEZNY0TGZMMgkBZGIsyLfpsP01dmZAm', 'CASHIER');

INSERT INTO categories (name) VALUES
    ('Sembako'),
    ('Minuman'),
    ('Makanan'),
    ('Kebutuhan Rumah');

INSERT INTO products (sku, barcode, name, category_id, unit, cost_price, sell_price, stock, min_stock)
SELECT p.sku, p.barcode, p.name, c.id, p.unit, p.cost_price, p.sell_price, p.stock, p.min_stock
FROM (VALUES
    (1, 'BRS-005', '8991234567883', 'Beras Premium 5 kg', 'Sembako',         'KG',    70000, 78000,  24, 10),
    (2, 'MYK-002', '8991234567890', 'Minyak Goreng 2 L',  'Sembako',         'LITER', 32000, 36500,   3, 12),
    (3, 'GLA-001', '8991234567906', 'Gula Pasir 1 kg',    'Sembako',         'KG',    15500, 17500,  40, 15),
    (4, 'TLR-001', '8991234567913', 'Telur Ayam 1 kg',    'Sembako',         'KG',    26000, 29000,   8, 10),
    (5, 'MIE-001', '8991234567920', 'Mi Instan Goreng',   'Makanan',         'PCS',    2800,  3500, 120, 48),
    (6, 'AIR-600', '8991234567937', 'Air Mineral 600 ml', 'Minuman',         'PCS',    3000,  4000,   0, 24),
    (7, 'SBN-001', '8991234567944', 'Sabun Mandi Batang', 'Kebutuhan Rumah', 'PCS',    3500,  4500,  36, 12),
    (8, 'KPI-001', '8991234567951', 'Kopi Sachet',        'Minuman',         'PCS',    1500,  2000,  85, 30)
) AS p(ord, sku, barcode, name, category, unit, cost_price, sell_price, stock, min_stock)
JOIN categories c ON c.name = p.category
ORDER BY p.ord;

-- products.stock is a cache; record the initial stock as IN movements so history matches.
INSERT INTO stock_movements (product_id, type, qty, stock_before, stock_after, note, user_id)
SELECT p.id, 'IN', p.stock, 0, p.stock, 'Stok awal', u.id
FROM products p
CROSS JOIN users u
WHERE u.username = 'budi' AND p.stock > 0
ORDER BY p.id;

UPDATE store_settings
SET store_name     = 'Toko Berkah Jaya',
    address        = 'Jl. Kaliurang Km 5, Yogyakarta',
    phone          = '0274-123456',
    receipt_footer = 'Terima kasih, selamat belanja kembali'
WHERE id = 1;
