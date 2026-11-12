package com.yoedi.epos.stock;

import com.yoedi.epos.product.Product;
import com.yoedi.epos.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * One row of stock history; the source of truth for stock (products.stock is a cache).
 * Append-only: rows are never updated or deleted. {@code qty} is positive for stock in
 * and negative for stock out. {@code saleId} is a plain id (not an entity reference) so
 * the stock package does not depend on the sale package.
 */
@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20, updatable = false)
    private StockMovementType type;

    @Column(name = "qty", nullable = false, updatable = false)
    private int qty;

    @Column(name = "stock_before", nullable = false, updatable = false)
    private int stockBefore;

    @Column(name = "stock_after", nullable = false, updatable = false)
    private int stockAfter;

    @Column(name = "reason", length = 20, updatable = false)
    private String reason;

    @Column(name = "note", length = 255, updatable = false)
    private String note;

    @Column(name = "sale_id", updatable = false)
    private Long saleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
