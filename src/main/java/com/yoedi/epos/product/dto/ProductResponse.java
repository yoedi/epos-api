package com.yoedi.epos.product.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Product object shared by list, get, create, and update (docs/api-spec.md §3).
 * {@code costPrice} is omitted for the CASHIER role (controlled by the service)
 * and {@code stockStatus} is computed from stock vs min_stock.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProductResponse(
        long id,
        String sku,
        String barcode,
        String name,
        CategoryRefResponse category,
        String unit,
        Long costPrice,
        long sellPrice,
        int stock,
        int minStock,
        String stockStatus,
        boolean isActive
) {
}
