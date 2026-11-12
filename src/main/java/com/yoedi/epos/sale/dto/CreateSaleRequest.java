package com.yoedi.epos.sale.dto;

import com.yoedi.epos.sale.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Checkout payload (docs/api-spec.md §4 POST /sales). The client sends only product ids and
 * quantities; prices and totals are always taken from the database.
 * <p>
 * An empty {@code items} list is deliberately not a validation error: the contract answers it
 * with 422 EMPTY_CART, which {@code SaleService} raises.
 */
public record CreateSaleRequest(
        @NotNull(message = "Daftar item wajib diisi")
        @Valid
        List<SaleItemRequest> items,

        @NotNull(message = "Metode pembayaran wajib diisi")
        PaymentMethod paymentMethod,

        @NotNull(message = "Uang diterima wajib diisi")
        @PositiveOrZero(message = "Uang diterima tidak boleh negatif")
        Long paid
) {
}
