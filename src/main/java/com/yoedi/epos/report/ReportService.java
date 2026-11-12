package com.yoedi.epos.report;

import com.yoedi.epos.product.ProductService;
import com.yoedi.epos.product.dto.LowStockProductResponse;
import com.yoedi.epos.product.dto.ProductRefResponse;
import com.yoedi.epos.report.dto.DailyReportResponse;
import com.yoedi.epos.report.dto.TopProductResponse;
import com.yoedi.epos.sale.InvoiceNumberGenerator;
import com.yoedi.epos.sale.SaleService;
import com.yoedi.epos.sale.dto.HourlySalesResponse;
import com.yoedi.epos.sale.dto.ProductSalesSummary;
import com.yoedi.epos.sale.dto.SaleSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Daily report for the dashboard. It only assembles what {@link SaleService} and
 * {@link ProductService} already compute; it never touches their repositories.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    /** The dashboard shows the best 5 sellers (docs/api-spec.md §6). */
    static final int TOP_PRODUCTS_LIMIT = 5;

    private final SaleService saleService;
    private final ProductService productService;

    /**
     * @param date calendar day in the store time zone (Asia/Jakarta); today when {@code null}.
     *             A future day is allowed and simply has no sales.
     *             Low stock is always the current stock, whatever the date: it is not kept historically.
     */
    // REPEATABLE_READ so every figure comes from one snapshot: a sale committed halfway through
    // must not show up in the hourly totals but not in the summary.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public DailyReportResponse daily(LocalDate date) {
        LocalDate day = date == null ? LocalDate.now(InvoiceNumberGenerator.BUSINESS_ZONE) : date;

        SaleSummaryResponse summary = saleService.summarizeDay(day);
        List<HourlySalesResponse> hourlySales = saleService.hourlySales(day);
        List<TopProductResponse> topProducts = topProducts(day);
        List<LowStockProductResponse> lowStockProducts = productService.findLowStock();

        return new DailyReportResponse(day, summary.totalRevenue(), summary.transactionCount(),
                summary.averageTransaction(), lowStockProducts.size(), hourlySales, topProducts, lowStockProducts);
    }

    private List<TopProductResponse> topProducts(LocalDate day) {
        List<ProductSalesSummary> sold = saleService.topProducts(day, TOP_PRODUCTS_LIMIT);
        Map<Long, ProductRefResponse> products = productService.findRefsByIds(
                sold.stream().map(ProductSalesSummary::productId).toList());
        return sold.stream()
                .map(row -> {
                    ProductRefResponse product = products.get(row.productId());
                    return new TopProductResponse(row.productId(), product.sku(), product.name(),
                            row.qtySold(), row.total());
                })
                .toList();
    }
}
