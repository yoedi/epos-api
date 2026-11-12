package com.yoedi.epos.sale;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleCalculatorTest {

    @Test
    void itemSubtotal_isPriceTimesQty() {
        assertThat(SaleCalculator.itemSubtotal(3500, 5)).isEqualTo(17500);
        assertThat(SaleCalculator.itemSubtotal(78000, 1)).isEqualTo(78000);
    }

    @Test
    void itemSubtotal_allowsZeroPrice() {
        assertThat(SaleCalculator.itemSubtotal(0, 3)).isZero();
    }

    @Test
    void itemSubtotal_rejectsNonPositiveQty() {
        assertThatThrownBy(() -> SaleCalculator.itemSubtotal(1000, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaleCalculator.itemSubtotal(1000, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemSubtotal_rejectsNegativePrice() {
        assertThatThrownBy(() -> SaleCalculator.itemSubtotal(-1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void itemSubtotal_failsOnOverflow() {
        assertThatThrownBy(() -> SaleCalculator.itemSubtotal(Long.MAX_VALUE, 2))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void subtotal_sumsItemSubtotals() {
        // Sample cart from the PRD: Beras ×1, Mi Instan ×5, Telur ×1, Kopi Sachet ×10
        long subtotal = SaleCalculator.subtotal(List.of(
                SaleCalculator.itemSubtotal(78000, 1),
                SaleCalculator.itemSubtotal(3500, 5),
                SaleCalculator.itemSubtotal(29000, 1),
                SaleCalculator.itemSubtotal(2000, 10)));

        assertThat(subtotal).isEqualTo(144500);
    }

    @Test
    void subtotal_ofEmptyListIsZero() {
        assertThat(SaleCalculator.subtotal(List.of())).isZero();
    }

    @Test
    void subtotal_failsOnOverflow() {
        assertThatThrownBy(() -> SaleCalculator.subtotal(List.of(Long.MAX_VALUE, 1L)))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void total_withZeroDiscountEqualsSubtotal() {
        assertThat(SaleCalculator.total(144500, 0)).isEqualTo(144500);
    }

    @Test
    void total_subtractsDiscount() {
        assertThat(SaleCalculator.total(144500, 4500)).isEqualTo(140000);
    }

    @Test
    void total_rejectsInvalidDiscount() {
        assertThatThrownBy(() -> SaleCalculator.total(1000, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SaleCalculator.total(1000, 1001))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void change_isPaidMinusTotal() {
        assertThat(SaleCalculator.change(150000, 144500)).isEqualTo(5500);
    }

    @Test
    void change_isZeroForExactPayment() {
        assertThat(SaleCalculator.change(144500, 144500)).isZero();
    }

    @Test
    void change_rejectsPaymentBelowTotal() {
        assertThatThrownBy(() -> SaleCalculator.change(100000, 144500))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
