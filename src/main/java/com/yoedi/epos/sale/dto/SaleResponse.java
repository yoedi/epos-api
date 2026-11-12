package com.yoedi.epos.sale.dto;

import com.yoedi.epos.sale.PaymentMethod;
import com.yoedi.epos.sale.SaleStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Sale object returned by POST /sales (docs/api-spec.md §4). Field names are part of the contract. */
public record SaleResponse(
        long id,
        String invoiceNo,
        CashierRefResponse cashier,
        List<SaleItemResponse> items,
        long subtotal,
        long discount,
        long total,
        PaymentMethod paymentMethod,
        long paid,
        long changeAmount,
        SaleStatus status,
        OffsetDateTime createdAt
) {
}
