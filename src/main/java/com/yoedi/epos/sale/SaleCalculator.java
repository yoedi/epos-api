package com.yoedi.epos.sale;

import java.util.Collection;

/**
 * Pure money calculations for a sale. No Spring, no database.
 * <p>
 * All amounts are whole rupiah ({@code long}). Arithmetic uses exact operations so an
 * overflow fails loudly instead of silently wrapping around.
 * <p>
 * Business validation that must surface as an API error (insufficient payment, stock, etc.)
 * is done in {@code SaleService}; the guards here only catch programming errors.
 */
public final class SaleCalculator {

    private SaleCalculator() {
    }

    /** Subtotal of one line: {@code price * qty}. */
    public static long itemSubtotal(long price, int qty) {
        if (price < 0) {
            throw new IllegalArgumentException("price must not be negative: " + price);
        }
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive: " + qty);
        }
        return Math.multiplyExact(price, (long) qty);
    }

    /** Sum of all line subtotals, before discount. */
    public static long subtotal(Collection<Long> itemSubtotals) {
        long sum = 0;
        for (long itemSubtotal : itemSubtotals) {
            if (itemSubtotal < 0) {
                throw new IllegalArgumentException("item subtotal must not be negative: " + itemSubtotal);
            }
            sum = Math.addExact(sum, itemSubtotal);
        }
        return sum;
    }

    /** Amount to pay: {@code subtotal - discount}. Discount is always 0 in v1. */
    public static long total(long subtotal, long discount) {
        if (discount < 0 || discount > subtotal) {
            throw new IllegalArgumentException(
                    "discount must be between 0 and subtotal: discount=" + discount + ", subtotal=" + subtotal);
        }
        return subtotal - discount;
    }

    /**
     * Change returned to the customer: {@code paid - total}.
     * The caller must check {@code paid >= total} first and raise the proper business error.
     */
    public static long change(long paid, long total) {
        if (paid < total) {
            throw new IllegalArgumentException("paid must be at least total: paid=" + paid + ", total=" + total);
        }
        return paid - total;
    }
}
