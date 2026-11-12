package com.yoedi.epos.sale.dto;

/**
 * Quantity and revenue of one product over a period. Also the target of the JPQL constructor
 * expression in {@code SaleRepository}, so the constructor order matters. Name and SKU are not
 * here: the report adds the product's current ones.
 */
public record ProductSalesSummary(
        long productId,
        long qtySold,
        long total
) {
}
