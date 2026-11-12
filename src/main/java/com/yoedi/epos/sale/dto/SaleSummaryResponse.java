package com.yoedi.epos.sale.dto;

/** Aggregates over every sale matching the filter, not just the current page. */
public record SaleSummaryResponse(
        long totalRevenue,
        long transactionCount,
        long averageTransaction
) {
}
