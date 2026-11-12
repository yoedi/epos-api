package com.yoedi.epos.sale.dto;

/** Minimal cashier reference embedded in a sale response. */
public record CashierRefResponse(
        long id,
        String name
) {
}
