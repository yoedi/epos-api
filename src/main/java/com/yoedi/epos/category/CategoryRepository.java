package com.yoedi.epos.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Category> findByNameIgnoreCase(String name);

    /**
     * Product count for one category. Native query on purpose: ProductService will depend on
     * CategoryService (category validation), so CategoryService must not depend on the product
     * feature, otherwise we get a service dependency cycle.
     */
    @Query(value = "select count(*) from products p where p.category_id = :categoryId", nativeQuery = true)
    long countProducts(@Param("categoryId") long categoryId);

    /** All categories with their product counts in a single query, ordered by name (case-insensitive). */
    @Query(value = """
            select c.id as id, c.name as name, count(p.id) as "productCount"
            from categories c
            left join products p on p.category_id = c.id
            group by c.id, c.name
            order by lower(c.name)
            """, nativeQuery = true)
    List<CategoryWithCount> findAllWithProductCount();
}
