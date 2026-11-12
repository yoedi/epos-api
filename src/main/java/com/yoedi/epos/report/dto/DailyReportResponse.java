package com.yoedi.epos.report.dto;

import com.yoedi.epos.product.dto.LowStockProductResponse;
import com.yoedi.epos.sale.dto.HourlySalesResponse;

import java.time.LocalDate;
import java.util.List;

/** GET /reports/daily (docs/api-spec.md §6). Serialized as snake_case by the global Jackson config. */
public record DailyReportResponse(
        LocalDate date,
        long totalRevenue,
        long transactionCount,
        long averageTransaction,
        int lowStockCount,
        List<HourlySalesResponse> hourlySales,
        List<TopProductResponse> topProducts,
        List<LowStockProductResponse> lowStockProducts
) {
}
