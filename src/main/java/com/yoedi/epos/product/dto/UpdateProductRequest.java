package com.yoedi.epos.product.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Partial update for a product (docs/api-spec.md §3 PATCH /products/{id}).
 * A null field means "not sent" and is left unchanged; a field that is sent must still
 * be valid (e.g. not blank). {@code stock} is deliberately absent because stock changes
 * must go through the stock endpoints.
 */
public record UpdateProductRequest(
        @Size(max = 50, message = "SKU maksimal 50 karakter")
        @Pattern(regexp = ".*\\S.*", message = "SKU tidak boleh kosong")
        String sku,

        // Blank barcode is allowed on purpose: it clears the barcode.
        @Size(max = 50, message = "Barcode maksimal 50 karakter")
        String barcode,

        @Size(max = 150, message = "Nama produk maksimal 150 karakter")
        @Pattern(regexp = ".*\\S.*", message = "Nama produk tidak boleh kosong")
        String name,

        Long categoryId,

        @Pattern(regexp = "PCS|KG|LITER|BUNGKUS|DUS", message = "Satuan tidak valid")
        String unit,

        @PositiveOrZero(message = "Harga modal tidak boleh negatif")
        Long costPrice,

        @PositiveOrZero(message = "Harga jual tidak boleh negatif")
        Long sellPrice,

        @PositiveOrZero(message = "Stok minimum tidak boleh negatif")
        Integer minStock
) {
}
