package com.yoedi.epos.product.dto;

/** Minimal category reference embedded in a product response (docs/api-spec.md §3). */
public record CategoryRefResponse(
        long id,
        String name
) {
}
