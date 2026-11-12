package com.yoedi.epos.stock.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Payload of POST /stock/in (docs/api-spec.md §5). One note applies to every item. */
public record StockInRequest(
        @Size(max = 255, message = "Catatan maksimal 255 karakter")
        String note,

        @NotEmpty(message = "Barang masuk minimal 1 produk")
        List<@Valid StockInItemRequest> items
) {
}
