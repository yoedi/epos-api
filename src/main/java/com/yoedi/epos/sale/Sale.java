package com.yoedi.epos.sale;

import com.yoedi.epos.user.User;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A completed checkout. Append-only: no updated_at, never edited after creation.
 * All money is whole rupiah as {@code long}; the totals are always computed by the server.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_no", nullable = false, length = 30, updatable = false)
    private String invoiceNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_id", nullable = false, updatable = false)
    private User cashier;

    @Column(name = "subtotal", nullable = false, updatable = false)
    private long subtotal;

    @Column(name = "discount", nullable = false, updatable = false)
    private long discount;

    @Column(name = "total", nullable = false, updatable = false)
    private long total;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20, updatable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "paid", nullable = false, updatable = false)
    private long paid;

    @Column(name = "change_amount", nullable = false, updatable = false)
    private long changeAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SaleStatus status;

    /** Set by the service so the invoice date and the timestamp come from the same instant. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SaleItem> items = new ArrayList<>();

    public void addItem(SaleItem item) {
        item.setSale(this);
        items.add(item);
    }
}
