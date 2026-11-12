package com.yoedi.epos.product.dto;

/** One row of "Stok Menipis" on the dashboard (docs/api-spec.md §6). */
public record LowStockProductResponse(
        long productId,
        String sku,
        String productName,
        int stock,
        int minStock,
        String stockStatus
) {
}
