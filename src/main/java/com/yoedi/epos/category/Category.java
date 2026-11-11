package com.yoedi.epos.category;

import com.yoedi.epos.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Product category. Names are unique case-insensitively (see uk_categories_name_ci).
 * A category can only be deleted when it has no products (ON DELETE RESTRICT).
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;
}
