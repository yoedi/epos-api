package com.yoedi.epos.product;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProductServiceStockStatusTest {

    @Test
    void stockStatus_outOfStock_whenStockIsZero() {
        assertThat(ProductService.stockStatus(0, 10)).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void stockStatus_lowStock_whenStockAtOrBelowMinStock() {
        assertThat(ProductService.stockStatus(3, 12)).isEqualTo("LOW_STOCK");
        assertThat(ProductService.stockStatus(12, 12)).isEqualTo("LOW_STOCK");
    }

    @Test
    void stockStatus_safe_whenStockAboveMinStock() {
        assertThat(ProductService.stockStatus(24, 10)).isEqualTo("SAFE");
    }
}
