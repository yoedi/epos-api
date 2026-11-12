package com.yoedi.epos.product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsByBarcode(String barcode);

    Optional<Product> findBySku(String sku);

    Optional<Product> findByBarcode(String barcode);

    Optional<Product> findBySkuAndIdNot(String sku, long id);

    Optional<Product> findByBarcodeAndIdNot(String barcode, long id);

    /**
     * List products with optional filters: text search (name, SKU, barcode), category,
     * and life status. {@code lowStockOnly} matches products where stock is not empty
     * but at or below min_stock (the LOW_STOCK status filter).
     */
    @Query("""
            select p from Product p
            where (:search is null
                   or lower(p.name) like lower(concat('%', :search, '%'))
                   or lower(p.sku) like lower(concat('%', :search, '%'))
                   or lower(coalesce(p.barcode, '')) like lower(concat('%', :search, '%')))
              and (:categoryId is null or p.category.id = :categoryId)
              and (:onlyActive is null or p.active = :onlyActive)
              and (:lowStockOnly is null or (p.stock > 0 and p.stock <= p.minStock))
            """)
    Page<Product> search(@Param("search") String search,
                         @Param("categoryId") Long categoryId,
                         @Param("onlyActive") Boolean onlyActive,
                         @Param("lowStockOnly") Boolean lowStockOnly,
                         Pageable pageable);

    /**
     * Loads products with {@code SELECT ... FOR UPDATE}, sorted by id. Always lock in id order:
     * two transactions buying the same products in a different order would otherwise deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
