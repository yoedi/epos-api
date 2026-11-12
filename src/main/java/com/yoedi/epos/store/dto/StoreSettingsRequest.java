package com.yoedi.epos.store.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload of PUT /store-settings: the same object that GET returns (see docs/api-spec.md §7). */
public record StoreSettingsRequest(
        @NotBlank(message = "Nama toko wajib diisi")
        @Size(max = 100, message = "Nama toko maksimal 100 karakter")
        String storeName,

        @Size(max = 255, message = "Alamat maksimal 255 karakter")
        String address,

        @Size(max = 30, message = "Nomor telepon maksimal 30 karakter")
        String phone,

        @Size(max = 255, message = "Teks footer struk maksimal 255 karakter")
        String receiptFooter
) {
}
