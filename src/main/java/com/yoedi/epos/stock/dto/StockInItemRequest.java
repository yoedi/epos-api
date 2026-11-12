package com.yoedi.epos.stock.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StockInItemRequest(
        @NotNull(message = "Produk wajib diisi")
        Long productId,

        @NotNull(message = "Jumlah wajib diisi")
        @Min(value = 1, message = "Jumlah harus lebih dari 0")
        @Max(value = 1_000_000, message = "Jumlah maksimal 1.000.000")
        Integer qty
) {
}
