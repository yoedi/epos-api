package com.yoedi.epos.sale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of a sale. {@code productName}, {@code unit}, and {@code price} are a snapshot taken
 * at the time of the sale, so later product edits never change past receipts.
 */
@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
public class SaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false, updatable = false)
    private Sale sale;

    @Column(name = "product_id", nullable = false, updatable = false)
    private long productId;

    @Column(name = "product_name", nullable = false, length = 150, updatable = false)
    private String productName;

    @Column(name = "unit", nullable = false, length = 20, updatable = false)
    private String unit;

    @Column(name = "price", nullable = false, updatable = false)
    private long price;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;

    @Column(name = "subtotal", nullable = false, updatable = false)
    private long subtotal;
}
