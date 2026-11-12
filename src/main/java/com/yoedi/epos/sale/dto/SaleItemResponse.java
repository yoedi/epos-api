package com.yoedi.epos.sale.dto;

public record SaleItemResponse(
        long productId,
        String productName,
        String unit,
        long price,
        int qty,
        long subtotal
) {
}
