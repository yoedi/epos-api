package com.yoedi.epos.store.dto;

/** Response shape shared by GET and PUT (see docs/api-spec.md §7). */
public record StoreSettingsResponse(
        String storeName,
        String address,
        String phone,
        String receiptFooter
) {
}
