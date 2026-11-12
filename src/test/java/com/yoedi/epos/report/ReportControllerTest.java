package com.yoedi.epos.report;

import com.yoedi.epos.TestAuth;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.Category;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.ProductRepository;
import com.yoedi.epos.sale.InvoiceNumberGenerator;
import com.yoedi.epos.sale.SaleTestData;
import com.yoedi.epos.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/reports/daily: response shape, the Asia/Jakarta day and hour boundaries, and OWNER-only access. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SaleTestData.class})
class ReportControllerTest {

    private static final java.time.ZoneId ZONE = InvoiceNumberGenerator.BUSINESS_ZONE;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 16);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SaleTestData data;
    @Autowired
    private ProductRepository productRepository;

    private User owner;
    private User rina;
    private Category category;

    @BeforeEach
    void setUp() {
        data.clear();
        owner = data.owner();
        rina = data.cashier();
        category = data.category();
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    void returns_the_contract_shape_for_a_day_with_sales() throws Exception {
        Product beras = product("BRS-005", "Beras Premium 5 kg", 10_000, 50, 10);
        data.sale(rina, invoice(DAY, 1), at(DAY, 8, 15), beras, 3);   // 30.000
        data.sale(rina, invoice(DAY, 2), at(DAY, 8, 45), beras, 1);   // 10.000
        data.sale(rina, invoice(DAY, 3), at(DAY, 14, 5), beras, 2);   // 20.000

        daily("?date=" + DAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-09-16"))
                .andExpect(jsonPath("$.total_revenue").value(60_000))
                .andExpect(jsonPath("$.transaction_count").value(3))
                .andExpect(jsonPath("$.average_transaction").value(20_000))
                .andExpect(jsonPath("$.low_stock_count").value(0))
                .andExpect(jsonPath("$.hourly_sales.length()").value(2))
                .andExpect(jsonPath("$.hourly_sales[0].hour").value(8))
                .andExpect(jsonPath("$.hourly_sales[0].total").value(40_000))
                .andExpect(jsonPath("$.hourly_sales[0].transaction_count").value(2))
                .andExpect(jsonPath("$.hourly_sales[1].hour").value(14))
                .andExpect(jsonPath("$.hourly_sales[1].total").value(20_000))
                .andExpect(jsonPath("$.hourly_sales[1].transaction_count").value(1))
                .andExpect(jsonPath("$.top_products.length()").value(1))
                .andExpect(jsonPath("$.top_products[0].product_id").value(beras.getId()))
                .andExpect(jsonPath("$.top_products[0].sku").value("BRS-005"))
                .andExpect(jsonPath("$.top_products[0].product_name").value("Beras Premium 5 kg"))
                .andExpect(jsonPath("$.top_products[0].qty_sold").value(6))
                .andExpect(jsonPath("$.top_products[0].total").value(60_000))
                .andExpect(jsonPath("$.low_stock_products.length()").value(0));
    }

    @Test
    void a_day_without_sales_is_all_zeros_and_empty_lists() throws Exception {
        daily("?date=" + DAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_revenue").value(0))
                .andExpect(jsonPath("$.transaction_count").value(0))
                .andExpect(jsonPath("$.average_transaction").value(0))
                .andExpect(jsonPath("$.hourly_sales").isEmpty())
                .andExpect(jsonPath("$.top_products").isEmpty());
    }

    @Test
    void date_defaults_to_today_in_the_store_time_zone() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        Product beras = product("BRS-005", "Beras Premium 5 kg", 10_000, 50, 10);
        data.sale(rina, invoice(today, 1), at(today, 0, 1), beras, 2);

        daily("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value(today.toString()))
                .andExpect(jsonPath("$.total_revenue").value(20_000))
                .andExpect(jsonPath("$.hourly_sales[0].hour").value(0));
    }

    @Test
    void the_day_runs_from_midnight_to_midnight_Asia_Jakarta() throws Exception {
        Product beras = product("BRS-005", "Beras Premium 5 kg", 10_000, 50, 10);
        data.sale(rina, invoice(DAY.minusDays(1), 1), at(DAY.minusDays(1), 23, 59), beras, 9); // previous day
        data.sale(rina, invoice(DAY, 1), at(DAY, 0, 0), beras, 1);                             // first minute
        data.sale(rina, invoice(DAY, 2), at(DAY, 23, 59), beras, 2);                           // last minute
        data.sale(rina, invoice(DAY.plusDays(1), 1), at(DAY.plusDays(1), 0, 0), beras, 7);     // next day

        // 00:00 and 23:59 Jakarta are 17:00 and 16:59 UTC: they must be grouped by the Jakarta hour.
        daily("?date=" + DAY)
                .andExpect(jsonPath("$.transaction_count").value(2))
                .andExpect(jsonPath("$.total_revenue").value(30_000))
                .andExpect(jsonPath("$.hourly_sales.length()").value(2))
                .andExpect(jsonPath("$.hourly_sales[0].hour").value(0))
                .andExpect(jsonPath("$.hourly_sales[0].total").value(10_000))
                .andExpect(jsonPath("$.hourly_sales[1].hour").value(23))
                .andExpect(jsonPath("$.hourly_sales[1].total").value(20_000))
                .andExpect(jsonPath("$.top_products[0].qty_sold").value(3));
    }

    @Test
    void average_is_a_whole_rupiah_rounded_half_up_like_sales_history() throws Exception {
        Product permen = product("PRM-001", "Permen", 1, 500, 0);
        data.sale(rina, invoice(DAY, 1), at(DAY, 9, 0), permen, 1);
        data.sale(rina, invoice(DAY, 2), at(DAY, 9, 5), permen, 2);

        // 3 / 2 = 1.5 -> 2 (half up)
        daily("?date=" + DAY).andExpect(jsonPath("$.average_transaction").value(2));

        // 4 / 3 = 1.33 -> 1
        data.sale(rina, invoice(DAY, 3), at(DAY, 9, 10), permen, 1);
        daily("?date=" + DAY).andExpect(jsonPath("$.average_transaction").value(1));
    }

    @Test
    void top_products_are_limited_to_5_sorted_by_total_descending_and_sum_across_sales() throws Exception {
        Product p1 = product("P-1", "Produk 1", 1_000, 50, 0);
        Product p2 = product("P-2", "Produk 2", 2_000, 50, 0);
        Product p3 = product("P-3", "Produk 3", 3_000, 50, 0);
        Product p4 = product("P-4", "Produk 4", 4_000, 50, 0);
        Product p5 = product("P-5", "Produk 5", 5_000, 50, 0);
        Product p6 = product("P-6", "Produk 6", 6_000, 50, 0);
        data.sale(rina, invoice(DAY, 1), at(DAY, 9, 0), p1, 1);      // 1.000: the only one cut off
        data.sale(rina, invoice(DAY, 2), at(DAY, 9, 1), p2, 1);      // 2.000
        data.sale(rina, invoice(DAY, 3), at(DAY, 9, 2), p3, 1);      // 3.000
        data.sale(rina, invoice(DAY, 4), at(DAY, 9, 3), p4, 1);      // 4.000
        data.sale(rina, invoice(DAY, 5), at(DAY, 9, 4), p5, 1);      // 5.000
        data.sale(rina, invoice(DAY, 6), at(DAY, 9, 5), p6, 1);      // 6.000
        data.sale(rina, invoice(DAY, 7), at(DAY, 10, 0), p2, 4);     // p2 total now 10.000 over 5 qty

        daily("?date=" + DAY)
                .andExpect(jsonPath("$.top_products.length()").value(5))
                .andExpect(jsonPath("$.top_products[0].sku").value("P-2"))
                .andExpect(jsonPath("$.top_products[0].qty_sold").value(5))
                .andExpect(jsonPath("$.top_products[0].total").value(10_000))
                .andExpect(jsonPath("$.top_products[1].sku").value("P-6"))
                .andExpect(jsonPath("$.top_products[2].sku").value("P-5"))
                .andExpect(jsonPath("$.top_products[3].sku").value("P-4"))
                .andExpect(jsonPath("$.top_products[4].sku").value("P-3"))
                .andExpect(jsonPath("$.top_products[?(@.sku == 'P-1')]").isEmpty());
    }

    @Test
    void low_stock_lists_active_products_at_or_below_min_stock_including_empty_ones_emptiest_first() throws Exception {
        product("LOW-1", "Minyak Goreng 2 L", 36_500, 3, 12);       // LOW_STOCK
        product("EQL-1", "Gula Pasir 1 kg", 15_000, 10, 10);        // stock == min_stock: LOW_STOCK
        product("OUT-1", "Telur Ayam 1 kg", 28_000, 0, 5);          // OUT_OF_STOCK
        product("SAF-1", "Beras Premium 5 kg", 78_000, 11, 10);     // SAFE: not listed
        Product inactive = product("INA-1", "Kopi Sachet", 2_000, 1, 5);
        inactive.setActive(false);
        productRepository.save(inactive);                           // inactive: not listed

        daily("?date=" + DAY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.low_stock_count").value(3))
                .andExpect(jsonPath("$.low_stock_products.length()").value(3))
                .andExpect(jsonPath("$.low_stock_products[0].sku").value("OUT-1"))
                .andExpect(jsonPath("$.low_stock_products[0].stock").value(0))
                .andExpect(jsonPath("$.low_stock_products[0].min_stock").value(5))
                .andExpect(jsonPath("$.low_stock_products[0].stock_status").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.low_stock_products[0].product_name").value("Telur Ayam 1 kg"))
                .andExpect(jsonPath("$.low_stock_products[0].product_id").isNumber())
                .andExpect(jsonPath("$.low_stock_products[1].sku").value("LOW-1"))
                .andExpect(jsonPath("$.low_stock_products[1].stock_status").value("LOW_STOCK"))
                .andExpect(jsonPath("$.low_stock_products[2].sku").value("EQL-1"))
                .andExpect(jsonPath("$.low_stock_products[2].stock_status").value("LOW_STOCK"));
    }

    @Test
    void cashier_gets403_FORBIDDEN() throws Exception {
        daily(rina, "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void without_token_returns401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/reports/daily"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void malformed_date_returns400_VALIDATION_ERROR() throws Exception {
        daily("?date=16-09-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions daily(String query) throws Exception {
        return daily(owner, query);
    }

    private ResultActions daily(User user, String query) throws Exception {
        return mockMvc.perform(get("/api/reports/daily" + query).with(TestAuth.as(user)));
    }

    /** A product with an explicit minimum stock (SaleTestData always uses 0). */
    private Product product(String sku, String name, long sellPrice, int stock, int minStock) {
        Product product = data.product(category, sku, name, sellPrice, stock);
        product.setMinStock(minStock);
        return productRepository.save(product);
    }

    private static String invoice(LocalDate date, int sequence) {
        return InvoiceNumberGenerator.format(date, sequence);
    }

    private static OffsetDateTime at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute).atZone(ZONE).toOffsetDateTime();
    }
}
