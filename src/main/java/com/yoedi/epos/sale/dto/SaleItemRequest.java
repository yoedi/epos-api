package com.yoedi.epos.sale.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SaleItemRequest(
        @NotNull(message = "Produk wajib diisi")
        Long productId,

        @NotNull(message = "Jumlah wajib diisi")
        @Positive(message = "Jumlah harus lebih dari 0")
        Integer qty
) {
}
