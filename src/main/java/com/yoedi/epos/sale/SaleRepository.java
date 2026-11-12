package com.yoedi.epos.sale;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

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
