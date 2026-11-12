package com.yoedi.epos.product;

import com.yoedi.epos.category.Category;
import com.yoedi.epos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A sellable item. SKU is unique; barcode is optional but unique when present.
 * {@code stock} is a cache of the {@code stock_movements} table, see AGENTS.md §5.
 * Money ({@code costPrice}, {@code sellPrice}) is whole rupiah as {@code long}.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class Product extends BaseEntity {

    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "barcode", length = 50)
    private String barcode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    /** PCS | KG | LITER | BUNGKUS | DUS (see docs/api-spec.md and PRD 14.3). */
    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "cost_price", nullable = false)
    private long costPrice;

    @Column(name = "sell_price", nullable = false)
    private long sellPrice;

    @Column(name = "stock", nullable = false)
    private int stock;

    @Column(name = "min_stock", nullable = false)
    private int minStock;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
