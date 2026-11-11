package com.yoedi.epos.category.dto;

/** Response shape shared by list, create, and update (see docs/api-spec.md §2). */
public record CategoryResponse(
        long id,
        String name,
        long productCount
) {
}
