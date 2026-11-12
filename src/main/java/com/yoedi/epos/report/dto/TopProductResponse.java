package com.yoedi.epos.report.dto;

/** A best-selling product of the day. {@code sku} and {@code productName} are the product's current ones. */
public record TopProductResponse(
        long productId,
        String sku,
        String productName,
        long qtySold,
        long total
) {
}
