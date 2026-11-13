package com.yoedi.epos.sale;

import com.yoedi.epos.TestAuth;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.product.Product;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/sales: response shape, summary, filters, and the CASHIER restriction. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SaleTestData.class})
class SaleHistoryControllerTest {

    private static final java.time.ZoneId ZONE = InvoiceNumberGenerator.BUSINESS_ZONE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SaleTestData data;

    private User owner;
    private User rina;
    private User sari;
    private Product beras;
    private LocalDate today;
    private LocalDate yesterday;

    @BeforeEach
    void setUp() {
        data.clear();
        owner = data.owner();
        rina = data.cashier();
        sari = otherCashier();
        beras = data.product(data.category(), "BRS-005", "Beras Premium 5 kg", 10_000, 100);
        today = LocalDate.now(ZONE);
        yesterday = today.minusDays(1);

        // Today: rina 2 sales (3 + 2 qty over two lines, then 1 qty), sari 1 sale.
        data.sale(rina, invoice(today, 1), at(today, 9), beras, 3, 2);   // 50.000, item_count 5
        data.sale(rina, invoice(today, 2), at(today, 11), beras, 1);     // 10.000
        data.sale(sari, invoice(today, 3), at(today, 13), beras, 4);     // 40.000
        // Yesterday: rina 1 sale (late evening, to exercise the Asia/Jakarta day boundary).
        data.sale(rina, invoice(yesterday, 1), at(yesterday, 23), beras, 7); // 70.000
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    void owner_defaults_to_today_for_all_cashiers_newest_first_with_the_contract_shape() throws Exception {
        history(owner, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].invoice_no").value(invoice(today, 3)))
                .andExpect(jsonPath("$.content[0].cashier_name").value("Sari Dewi"))
                .andExpect(jsonPath("$.content[0].item_count").value(4))
                .andExpect(jsonPath("$.content[0].total").value(40_000))
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andExpect(jsonPath("$.content[0].created_at").value(org.hamcrest.Matchers.endsWith("+07:00")))
                .andExpect(jsonPath("$.content[2].invoice_no").value(invoice(today, 1)))
                .andExpect(jsonPath("$.content[2].item_count").value(5)) // sum of qty, not number of lines
                .andExpect(jsonPath("$.summary.total_revenue").value(100_000))
                .andExpect(jsonPath("$.summary.transaction_count").value(3))
                .andExpect(jsonPath("$.summary.average_transaction").value(33_333))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void summary_covers_the_whole_filtered_result_not_only_the_page() throws Exception {
        history(owner, "?size=2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.summary.total_revenue").value(100_000))
                .andExpect(jsonPath("$.summary.transaction_count").value(3));
    }

    @Test
    void owner_filters_by_date_range_inclusive_in_the_store_time_zone() throws Exception {
        history(owner, "?start_date=" + yesterday + "&end_date=" + yesterday)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].invoice_no").value(invoice(yesterday, 1)))
                .andExpect(jsonPath("$.summary.total_revenue").value(70_000));

        history(owner, "?start_date=" + yesterday + "&end_date=" + today)
                .andExpect(jsonPath("$.summary.transaction_count").value(4))
                .andExpect(jsonPath("$.summary.total_revenue").value(170_000))
                .andExpect(jsonPath("$.summary.average_transaction").value(42_500));
    }

    @Test
    void owner_filters_by_cashier() throws Exception {
        history(owner, "?cashier_id=" + sari.getId())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].cashier_name").value("Sari Dewi"))
                .andExpect(jsonPath("$.summary.transaction_count").value(1));
    }

    @Test
    void owner_filters_by_invoice_number_containing_the_text_case_insensitively() throws Exception {
        history(owner, "?invoice_no=" + invoice(today, 2).toLowerCase())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].invoice_no").value(invoice(today, 2)))
                .andExpect(jsonPath("$.summary.total_revenue").value(10_000));

        history(owner, "?invoice_no=-0003")
                .andExpect(jsonPath("$.content.length()").value(1));
    }

    @Test
    void empty_result_has_a_zero_summary() throws Exception {
        history(owner, "?invoice_no=INV-NOPE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.summary.total_revenue").value(0))
                .andExpect(jsonPath("$.summary.transaction_count").value(0))
                .andExpect(jsonPath("$.summary.average_transaction").value(0))
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void cashier_only_sees_their_own_sales_of_today() throws Exception {
        history(rina, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].cashier_name").value("Rina Lestari"))
                .andExpect(jsonPath("$.content[1].cashier_name").value("Rina Lestari"))
                .andExpect(jsonPath("$.summary.total_revenue").value(60_000))
                .andExpect(jsonPath("$.summary.transaction_count").value(2));
    }

    @Test
    void cashier_cannot_widen_the_result_with_filters() throws Exception {
        // Another cashier's id and a period reaching yesterday are both ignored.
        history(rina, "?cashier_id=" + sari.getId() + "&start_date=" + yesterday + "&end_date=" + today)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.summary.total_revenue").value(60_000));

        // Asking only for yesterday does not return yesterday's sale either.
        history(rina, "?start_date=" + yesterday + "&end_date=" + yesterday)
                .andExpect(jsonPath("$.summary.transaction_count").value(2))
                .andExpect(jsonPath("$.content[?(@.invoice_no == '" + invoice(yesterday, 1) + "')]").isEmpty());
    }

    @Test
    void cashier_can_still_narrow_their_own_list_by_invoice_number() throws Exception {
        history(rina, "?invoice_no=" + invoice(today, 2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.summary.total_revenue").value(10_000));

        // Another cashier's invoice number finds nothing.
        history(rina, "?invoice_no=" + invoice(today, 3))
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void sorts_by_a_known_property() throws Exception {
        history(owner, "?sort=total,asc")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].total").value(10_000))
                .andExpect(jsonPath("$.content[2].total").value(50_000));
    }

    @Test
    void sorts_by_snake_case_names_and_rejects_camel_case() throws Exception {
        history(owner, "?sort=created_at,desc").andExpect(status().isOk());
        history(owner, "?sort=invoice_no,asc").andExpect(status().isOk());
        history(owner, "?sort=createdAt,desc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void sorting_by_an_unknown_property_returns400_VALIDATION_ERROR() throws Exception {
        history(owner, "?sort=foo,asc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void start_date_after_end_date_returns400_VALIDATION_ERROR() throws Exception {
        history(owner, "?start_date=" + today + "&end_date=" + yesterday)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.detail").value("Tanggal mulai tidak boleh setelah tanggal akhir"));
    }

    @Test
    void malformed_date_returns400_VALIDATION_ERROR() throws Exception {
        history(owner, "?start_date=16-09-2026")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void without_token_returns401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/sales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private ResultActions history(User user, String query) throws Exception {
        RequestPostProcessor auth = TestAuth.as(user);
        return mockMvc.perform(get("/api/sales" + query).with(auth));
    }

    private User otherCashier() {
        User user = new User();
        user.setName("Sari Dewi");
        user.setUsername("sari");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(com.yoedi.epos.user.Role.CASHIER);
        return data.userRepository.save(user);
    }

    private static String invoice(LocalDate date, int sequence) {
        return InvoiceNumberGenerator.format(date, sequence);
    }

    private static OffsetDateTime at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZONE).toOffsetDateTime();
    }
}
