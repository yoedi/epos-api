package com.yoedi.epos.category;

/** Projection for the category list: a category plus its product count, loaded in one query. */
public interface CategoryWithCount {

    Long getId();

    String getName();

    Long getProductCount();
}
