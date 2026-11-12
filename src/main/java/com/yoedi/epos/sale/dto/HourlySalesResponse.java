package com.yoedi.epos.sale.dto;

/**
 * Sales of one hour of the day (store time zone), used by {@code /reports/daily}.
 * Only hours that have transactions are returned; the frontend fills the empty hours with 0.
 */
public record HourlySalesResponse(
        int hour,
        long total,
        long transactionCount
) {
}
