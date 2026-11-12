package com.yoedi.epos.sale.dto;

import java.time.OffsetDateTime;

/**
 * One row of GET /sales (docs/api-spec.md §4). {@code itemCount} is the sum of qty over all
 * items, not the number of lines. Also the target of the JPQL constructor expression in
 * {@code SaleRepository}, so the constructor order matters.
 */
public record SaleListItemResponse(
        long id,
        String invoiceNo,
        String cashierName,
        long itemCount,
        long total,
        OffsetDateTime createdAt
) {
}
