package com.yoedi.epos.stock.dto;

import com.yoedi.epos.stock.StockAdjustmentReason;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Payload of POST /stock/adjustment. The client sends the physical count, not the difference. */
public record StockAdjustmentRequest(
        @NotNull(message = "Produk wajib diisi")
        Long productId,

        @NotNull(message = "Stok fisik wajib diisi")
        @Min(value = 0, message = "Stok fisik tidak boleh negatif")
        Integer physicalStock,

        @NotNull(message = "Alasan wajib diisi")
        StockAdjustmentReason reason,

        @Size(max = 255, message = "Catatan maksimal 255 karakter")
        String note
) {
}
