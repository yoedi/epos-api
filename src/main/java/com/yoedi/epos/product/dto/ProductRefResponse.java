package com.yoedi.epos.product.dto;

/** Minimal product identity (current SKU and name), for other features that only need to label a product id. */
public record ProductRefResponse(
        long id,
        String sku,
        String name
) {
}
