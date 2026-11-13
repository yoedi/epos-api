package com.yoedi.epos.sale;

import com.yoedi.epos.TestAuth;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/sales/{id}: response shape (sale + store), and the CASHIER restriction returning 404. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SaleTestData.class})
class SaleDetailControllerTest {

    private static final ZoneId ZONE = InvoiceNumberGenerator.BUSINESS_ZONE;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SaleTestData data;
    @Autowired
    private JdbcTemplate jdbc;

    private User owner;
    private User rina;
    private User sari;
    private Sale rinaToday;
    private Sale sariToday;
    private Sale rinaYesterday;

    @BeforeEach
    void setUp() {
        data.clear();
        jdbc.update("""
                update store_settings
                   set store_name = 'Toko Berkah', address = 'Jl. Kaliurang Km 5', phone = '0274-123456',
                       receipt_footer = 'Terima kasih' where id = 1
                """);
        owner = data.owner();
        rina = data.cashier();
        sari = otherCashier();
        Product beras = data.product(data.category(), "BRS-005", "Beras Premium 5 kg", 10_000, 100);
        LocalDate today = LocalDate.now(ZONE);
        LocalDate yesterday = today.minusDays(1);
        rinaToday = data.sale(rina, InvoiceNumberGenerator.format(today, 1), at(today, 9), beras, 3, 2);
        sariToday = data.sale(sari, InvoiceNumberGenerator.format(today, 2), at(today, 11), beras, 1);
        rinaYesterday = data.sale(rina, InvoiceNumberGenerator.format(yesterday, 1), at(yesterday, 23), beras, 7);
    }

    @AfterEach
    void tearDown() {
        data.clear();
        jdbc.update("""
                update store_settings
                   set store_name = 'Toko Saya', address = null, phone = null,
                       receipt_footer = 'Terima kasih, selamat belanja kembali' where id = 1
                """);
    }

    @Test
    void owner_gets_the_sale_with_items_and_store_in_the_contract_shape() throws Exception {
        detail(owner, rinaToday.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rinaToday.getId()))
                .andExpect(jsonPath("$.invoice_no").value(rinaToday.getInvoiceNo()))
                .andExpect(jsonPath("$.cashier.id").value(rina.getId()))
                .andExpect(jsonPath("$.cashier.name").value("Rina Lestari"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].product_name").value("Beras Premium 5 kg"))
                .andExpect(jsonPath("$.items[0].qty").value(3))
                .andExpect(jsonPath("$.items[0].subtotal").value(30_000))
                .andExpect(jsonPath("$.total").value(50_000))
                .andExpect(jsonPath("$.change_amount").isNumber())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.created_at").value(org.hamcrest.Matchers.endsWith("+07:00")))
                .andExpect(jsonPath("$.store.store_name").value("Toko Berkah"))
                .andExpect(jsonPath("$.store.address").value("Jl. Kaliurang Km 5"))
                .andExpect(jsonPath("$.store.phone").value("0274-123456"))
                .andExpect(jsonPath("$.store.receipt_footer").value("Terima kasih"));
    }

    @Test
    void owner_can_open_any_sale_including_older_ones() throws Exception {
        detail(owner, sariToday.getId()).andExpect(status().isOk());
        detail(owner, rinaYesterday.getId()).andExpect(status().isOk());
    }

    @Test
    void cashier_can_open_their_own_sale_of_today() throws Exception {
        detail(rina, rinaToday.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoice_no").value(rinaToday.getInvoiceNo()));
    }

    @Test
    void cashier_gets_404_for_another_cashiers_sale() throws Exception {
        detail(rina, sariToday.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void cashier_gets_404_for_their_own_sale_of_a_previous_day() throws Exception {
        detail(rina, rinaYesterday.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void unknown_id_returns_404_with_code() throws Exception {
        detail(owner, 999_999L)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void non_numeric_id_returns_400() throws Exception {
        mockMvc.perform(get("/api/sales/abc").with(TestAuth.as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void without_token_returns_401() throws Exception {
        mockMvc.perform(get("/api/sales/" + rinaToday.getId()))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions detail(User user, long id) throws Exception {
        return mockMvc.perform(get("/api/sales/" + id).with(TestAuth.as(user)));
    }

    private User otherCashier() {
        User user = new User();
        user.setName("Sari Dewi");
        user.setUsername("sari");
        user.setPasswordHash("$2a$10$hash");
        user.setRole(Role.CASHIER);
        return data.userRepository.save(user);
    }

    private static OffsetDateTime at(LocalDate date, int hour) {
        return date.atTime(hour, 0).atZone(ZONE).toOffsetDateTime();
    }
}
