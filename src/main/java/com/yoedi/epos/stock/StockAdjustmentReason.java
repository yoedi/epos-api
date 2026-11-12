package com.yoedi.epos.stock;

/** Allowed values of {@code reason} on an ADJUSTMENT movement; matches ck_stock_movements_reason. */
public enum StockAdjustmentReason {
    RUSAK,
    KEDALUWARSA,
    HILANG,
    SALAH_HITUNG,
    LAINNYA
}
