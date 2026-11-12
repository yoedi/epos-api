package com.yoedi.epos.stock.dto;

import com.yoedi.epos.stock.StockMovementType;

import java.time.OffsetDateTime;

/** Movement shape shared by stock in, adjustment, and the history list (docs/api-spec.md §5). */
public record StockMovementResponse(
        long id,
        long productId,
        String productName,
        StockMovementType type,
        int qty,
        int stockBefore,
        int stockAfter,
        String reason,
        String note,
        OffsetDateTime createdAt
) {
}
