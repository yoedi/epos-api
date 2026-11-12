package com.yoedi.epos.common.exception;

import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * {@code sort} names a property a list endpoint cannot order by. Mapped to 400 VALIDATION_ERROR.
 * Each list endpoint passes its own whitelist: an unknown property would otherwise fail inside the
 * query as a 500, and a hidden field (such as cost_price for a CASHIER) would still leak through ordering.
 */
public class InvalidSortException extends BusinessException {

    public InvalidSortException(String property) {
        super(ErrorCode.VALIDATION_ERROR, "Validasi gagal", "Pengurutan berdasarkan " + property + " tidak didukung");
    }

    /** Throws if {@code sort} contains a property that is not in {@code allowed} (Java property names). */
    public static void check(Sort sort, Set<String> allowed) {
        sort.forEach(order -> {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty());
            }
        });
    }
}
