package com.yoedi.epos.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Category> findByNameIgnoreCase(String name);

    /**
     * Number of products in a category. Native query so the category feature does not
     * need the (not yet built) Product JPA entity; it only needs the products table.
     */
    @Query(value = "select count(*) from products p where p.category_id = :categoryId", nativeQuery = true)
    long countProducts(@Param("categoryId") long categoryId);
}
