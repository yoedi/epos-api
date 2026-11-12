package com.yoedi.epos.sale.dto;

import com.yoedi.epos.common.dto.PageResponse;

import java.util.List;

/**
 * GET /sales response: the usual page fields plus {@code summary}. Same field names as
 * {@link PageResponse}, so the frontend reads the page part the same way everywhere.
 */
public record SalePageResponse(
        List<SaleListItemResponse> content,
        SaleSummaryResponse summary,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {

    public static SalePageResponse of(PageResponse<SaleListItemResponse> page, SaleSummaryResponse summary) {
        return new SalePageResponse(page.content(), summary, page.page(), page.size(),
                page.totalElements(), page.totalPages(), page.last());
    }
}
