package com.yoedi.epos.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Payload for creating a product (docs/api-spec.md §3 POST /products).
 * A positive {@code initialStock} is recorded as an IN movement with the note "Stok awal".
 */
public record CreateProductRequest(
        @NotBlank(message = "SKU wajib diisi")
        @Size(max = 50, message = "SKU maksimal 50 karakter")
        String sku,

        @Size(max = 50, message = "Barcode maksimal 50 karakter")
        String barcode,

        @NotBlank(message = "Nama produk wajib diisi")
        @Size(max = 150, message = "Nama produk maksimal 150 karakter")
        String name,

        @NotNull(message = "Kategori wajib diisi")
        Long categoryId,

        @NotBlank(message = "Satuan wajib diisi")
        @Pattern(regexp = "PCS|KG|LITER|BUNGKUS|DUS", message = "Satuan tidak valid")
        String unit,

        @NotNull(message = "Harga modal wajib diisi")
        @PositiveOrZero(message = "Harga modal tidak boleh negatif")
        Long costPrice,

        @NotNull(message = "Harga jual wajib diisi")
        @PositiveOrZero(message = "Harga jual tidak boleh negatif")
        Long sellPrice,

        @NotNull(message = "Stok awal wajib diisi")
        @PositiveOrZero(message = "Stok awal tidak boleh negatif")
        Integer initialStock,

        @NotNull(message = "Stok minimum wajib diisi")
        @PositiveOrZero(message = "Stok minimum tidak boleh negatif")
        Integer minStock
) {
}
