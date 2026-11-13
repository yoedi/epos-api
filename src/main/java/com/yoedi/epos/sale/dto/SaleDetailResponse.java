package com.yoedi.epos.sale.dto;

import com.yoedi.epos.sale.PaymentMethod;
import com.yoedi.epos.sale.SaleStatus;
import com.yoedi.epos.store.dto.StoreSettingsResponse;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response of GET /sales/{id} (docs/api-spec.md §4): the same fields as {@link SaleResponse}
 * plus {@code store}, so a receipt can be printed from one call.
 */
public record SaleDetailResponse(
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
        OffsetDateTime createdAt,
        StoreSettingsResponse store
) {

    public static SaleDetailResponse of(SaleResponse sale, StoreSettingsResponse store) {
        return new SaleDetailResponse(sale.id(), sale.invoiceNo(), sale.cashier(), sale.items(), sale.subtotal(),
                sale.discount(), sale.total(), sale.paymentMethod(), sale.paid(), sale.changeAmount(),
                sale.status(), sale.createdAt(), store);
    }
}
