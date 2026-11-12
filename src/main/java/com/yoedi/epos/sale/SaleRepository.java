package com.yoedi.epos.sale;

import com.yoedi.epos.sale.dto.ProductSalesSummary;
import com.yoedi.epos.sale.dto.SaleListItemResponse;
import com.yoedi.epos.sale.dto.SaleSummaryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    // The period is always bound (never null): a null timestamp parameter is sent untyped and PostgreSQL rejects it.
    // The invoice filter is a case-insensitive "contains"; the cast keeps a null parameter typed as text.
    String HISTORY_FILTER = """
            where s.createdAt >= :from and s.createdAt < :until
              and (:cashierId is null or s.cashier.id = :cashierId)
              and (cast(:invoiceNo as string) is null
                   or lower(s.invoiceNo) like lower(concat('%', cast(:invoiceNo as string), '%')))
            """;

    /** One page of sale rows for the history list. Item count is computed in SQL, so no items are loaded. */
    @Query(value = """
            select new com.yoedi.epos.sale.dto.SaleListItemResponse(
                s.id, s.invoiceNo, s.cashier.name,
                (select coalesce(sum(i.qty), 0L) from SaleItem i where i.sale = s),
                s.total, s.createdAt)
            from Sale s
            """ + HISTORY_FILTER,
            countQuery = "select count(s) from Sale s " + HISTORY_FILTER)
    Page<SaleListItemResponse> searchHistory(@Param("from") OffsetDateTime from,
                                             @Param("until") OffsetDateTime until,
                                             @Param("cashierId") Long cashierId,
                                             @Param("invoiceNo") String invoiceNo,
                                             Pageable pageable);

    /** Revenue and count over everything the filter matches (all pages). The average is left to the service. */
    @Query("""
            select new com.yoedi.epos.sale.dto.SaleSummaryResponse(coalesce(sum(s.total), 0L), count(s), 0L)
            from Sale s
            """ + HISTORY_FILTER)
    SaleSummaryResponse summarizeHistory(@Param("from") OffsetDateTime from,
                                         @Param("until") OffsetDateTime until,
                                         @Param("cashierId") Long cashierId,
                                         @Param("invoiceNo") String invoiceNo);

    /** One row of {@link #findHourlySales}; the getter names match the column aliases. */
    interface HourlySalesRow {
        int getHour();

        long getTotal();

        long getTransactionCount();
    }

    /**
     * Sales grouped by hour of day in {@code zone}, only hours that have sales. Native because the
     * hour has to be taken in the store time zone ({@code at time zone}); JPQL would use the
     * session zone (UTC). The casts keep the numeric types: sum(bigint) is numeric in PostgreSQL.
     */
    @Query(value = """
            select cast(extract(hour from s.created_at at time zone cast(:zone as text)) as integer) as "hour",
                   cast(sum(s.total) as bigint) as "total",
                   count(*) as "transactionCount"
            from sales s
            where s.created_at >= :from and s.created_at < :until
            group by 1
            order by 1
            """, nativeQuery = true)
    List<HourlySalesRow> findHourlySales(@Param("from") OffsetDateTime from,
                                         @Param("until") OffsetDateTime until,
                                         @Param("zone") String zone);

    /**
     * Best-selling products in the period by revenue, highest first (ties: lowest product id, so the
     * order is stable). The size comes from {@code pageable}.
     */
    @Query("""
            select new com.yoedi.epos.sale.dto.ProductSalesSummary(i.productId, sum(i.qty), sum(i.subtotal))
            from SaleItem i
            where i.sale.createdAt >= :from and i.sale.createdAt < :until
            group by i.productId
            order by sum(i.subtotal) desc, i.productId asc
            """)
    List<ProductSalesSummary> findTopProducts(@Param("from") OffsetDateTime from,
                                              @Param("until") OffsetDateTime until,
                                              Pageable pageable);

    /** Highest invoice number starting with {@code prefix} (e.g. {@code INV-20260916-}), if any. */
    @Query("select max(s.invoiceNo) from Sale s where s.invoiceNo like concat(:prefix, '%')")
    Optional<String> findLastInvoiceNo(@Param("prefix") String prefix);

    /**
     * Takes a transaction-scoped advisory lock (released on commit or rollback). Used to serialize
     * invoice numbering per day: without it two concurrent checkouts read the same "last invoice"
     * and the second one fails on uk_sales_invoice_no.
     * The cast is only there so the void result of pg_advisory_xact_lock can be read back.
     */
    @Query(value = "select cast(pg_advisory_xact_lock(:key) as text)", nativeQuery = true)
    String lockInvoiceSequence(@Param("key") long key);
}
