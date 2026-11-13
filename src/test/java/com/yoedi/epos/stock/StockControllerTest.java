package com.yoedi.epos.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoedi.epos.TestAuth;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.CategoryRepository;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.product.ProductRepository;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StockControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private StockMovementRepository stockMovementRepository;

    private User owner;
    private User cashier;
    private Product minyak;
    private Product beras;

    @BeforeEach
    void setUp() {
        clear();
        owner = user("budi", "Budi Santoso", Role.OWNER);
        cashier = user("rina", "Rina Lestari", Role.CASHIER);
        Category category = new Category();
        category.setName("Sembako");
        category = categoryRepository.save(category);
        minyak = product(category, "MYK-002", "Minyak Goreng 2 L", 3);
        beras = product(category, "BRS-005", "Beras Premium 5 kg", 24);
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    // ---- POST /stock/in ----------------------------------------------------------------------

    @Test
    void stockIn_returns201_with_movements_and_updates_stock() throws Exception {
        stockIn(owner, Map.of("note", "Belanja grosir 16 Sep", "items", List.of(
                Map.of("product_id", minyak.getId(), "qty", 24),
                Map.of("product_id", beras.getId(), "qty", 6))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movements.length()").value(2))
                .andExpect(jsonPath("$.movements[0].id").isNumber())
                .andExpect(jsonPath("$.movements[0].product_id").value(minyak.getId()))
                .andExpect(jsonPath("$.movements[0].product_name").value("Minyak Goreng 2 L"))
                .andExpect(jsonPath("$.movements[0].type").value("IN"))
                .andExpect(jsonPath("$.movements[0].qty").value(24))
                .andExpect(jsonPath("$.movements[0].stock_before").value(3))
                .andExpect(jsonPath("$.movements[0].stock_after").value(27))
                .andExpect(jsonPath("$.movements[0].note").value("Belanja grosir 16 Sep"))
                .andExpect(jsonPath("$.movements[0].created_at")
                        .value(org.hamcrest.Matchers.endsWith("+07:00")))
                .andExpect(jsonPath("$.movements[1].stock_after").value(30));

        assertThat(productRepository.findById(minyak.getId()).orElseThrow().getStock()).isEqualTo(27);
        assertThat(productRepository.findById(beras.getId()).orElseThrow().getStock()).isEqualTo(30);
    }

    @Test
    void stockIn_with_zero_qty_and_no_items_returns400_VALIDATION_ERROR() throws Exception {
        stockIn(owner, Map.of("items", List.of(Map.of("product_id", minyak.getId(), "qty", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].qty')]").exists());

        stockIn(owner, Map.of("items", List.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items')]").exists());
    }

    @Test
    void stockIn_with_unknown_product_returns404_and_saves_nothing() throws Exception {
        stockIn(owner, Map.of("items", List.of(
                Map.of("product_id", minyak.getId(), "qty", 5),
                Map.of("product_id", 999_999L, "qty", 1))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        assertThat(stockMovementRepository.count()).isZero();
        assertThat(productRepository.findById(minyak.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    // ---- POST /stock/adjustment --------------------------------------------------------------

    @Test
    void adjustment_returns201_and_sets_stock_to_the_physical_count() throws Exception {
        adjust(owner, Map.of("product_id", beras.getId(), "physical_stock", 20,
                "reason", "RUSAK", "note", "Kemasan bocor"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.reason").value("RUSAK"))
                .andExpect(jsonPath("$.qty").value(-4))
                .andExpect(jsonPath("$.stock_before").value(24))
                .andExpect(jsonPath("$.stock_after").value(20))
                .andExpect(jsonPath("$.note").value("Kemasan bocor"))
                .andExpect(jsonPath("$.product_name").value("Beras Premium 5 kg"));

        assertThat(productRepository.findById(beras.getId()).orElseThrow().getStock()).isEqualTo(20);
    }

    @Test
    void adjustment_can_raise_stock_and_can_set_it_to_zero() throws Exception {
        adjust(owner, Map.of("product_id", minyak.getId(), "physical_stock", 5, "reason", "SALAH_HITUNG"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qty").value(2));
        adjust(owner, Map.of("product_id", minyak.getId(), "physical_stock", 0, "reason", "KEDALUWARSA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.qty").value(-5))
                .andExpect(jsonPath("$.stock_after").value(0));
    }

    @Test
    void adjustment_without_difference_returns422_with_detail() throws Exception {
        adjust(owner, Map.of("product_id", beras.getId(), "physical_stock", 24, "reason", "LAINNYA"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("NO_STOCK_DIFFERENCE"))
                .andExpect(jsonPath("$.detail").value("Tidak ada selisih stok"))
                .andExpect(jsonPath("$.timestamp").exists());

        assertThat(stockMovementRepository.count()).isZero();
    }

    @Test
    void adjustment_with_invalid_body_returns400_VALIDATION_ERROR() throws Exception {
        adjust(owner, Map.of("product_id", beras.getId(), "physical_stock", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'physical_stock')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'reason')]").exists());
    }

    @Test
    void adjustment_with_unknown_reason_returns400_VALIDATION_ERROR() throws Exception {
        adjust(owner, Map.of("product_id", beras.getId(), "physical_stock", 20, "reason", "BOCOR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void adjustment_with_unknown_product_returns404_NOT_FOUND() throws Exception {
        adjust(owner, Map.of("product_id", 999_999L, "physical_stock", 1, "reason", "HILANG"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ---- GET /stock/movements ----------------------------------------------------------------

    @Test
    void movements_are_paged_newest_first_and_filterable() throws Exception {
        stockIn(owner, Map.of("items", List.of(Map.of("product_id", minyak.getId(), "qty", 10))))
                .andExpect(status().isCreated());
        adjust(owner, Map.of("product_id", minyak.getId(), "physical_stock", 12, "reason", "HILANG"))
                .andExpect(status().isCreated());
        stockIn(owner, Map.of("items", List.of(Map.of("product_id", beras.getId(), "qty", 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[0].product_id").value(beras.getId()))
                .andExpect(jsonPath("$.content[2].type").value("IN"))
                .andExpect(jsonPath("$.content[2].product_name").value("Minyak Goreng 2 L"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.total_elements").value(3))
                .andExpect(jsonPath("$.total_pages").value(1))
                .andExpect(jsonPath("$.last").value(true));

        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner))
                        .param("product_id", String.valueOf(minyak.getId())))
                .andExpect(jsonPath("$.total_elements").value(2))
                .andExpect(jsonPath("$.content[0].type").value("ADJUSTMENT"));

        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("type", "ADJUSTMENT"))
                .andExpect(jsonPath("$.total_elements").value(1));

        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("size", "2"))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.total_pages").value(2))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void movements_size_is_capped_at_100() throws Exception {
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void movements_filter_by_date_range_in_the_store_time_zone() throws Exception {
        stockIn(owner, Map.of("items", List.of(Map.of("product_id", minyak.getId(), "qty", 10))))
                .andExpect(status().isCreated());
        var today = java.time.LocalDate.now(com.yoedi.epos.config.JacksonConfig.STORE_ZONE);

        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner))
                        .param("start_date", today.toString()).param("end_date", today.toString()))
                .andExpect(jsonPath("$.total_elements").value(1));
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner))
                        .param("end_date", today.minusDays(1).toString()))
                .andExpect(jsonPath("$.total_elements").value(0));
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner))
                        .param("start_date", today.plusDays(1).toString()))
                .andExpect(jsonPath("$.total_elements").value(0));
    }

    @Test
    void movements_with_bad_filter_values_return400_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("type", "BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("start_date", "16-09-2026"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void movements_sort_by_a_known_property_and_reject_an_unknown_one() throws Exception {
        stockIn(owner, Map.of("items", List.of(Map.of("product_id", minyak.getId(), "qty", 10))))
                .andExpect(status().isCreated());
        stockIn(owner, Map.of("items", List.of(Map.of("product_id", beras.getId(), "qty", 1))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("sort", "qty,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].qty").value(1));
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("sort", "foo,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void movements_sort_by_snake_case_names_and_reject_camel_case() throws Exception {
        for (String property : List.of("created_at", "stock_before", "stock_after", "sale_id")) {
            mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("sort", property + ",desc"))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(owner)).param("sort", "createdAt,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- authorization -----------------------------------------------------------------------

    @Test
    void cashier_is_forbidden_on_every_stock_endpoint_and_nothing_changes() throws Exception {
        stockIn(cashier, Map.of("items", List.of(Map.of("product_id", minyak.getId(), "qty", 5))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        adjust(cashier, Map.of("product_id", minyak.getId(), "physical_stock", 1, "reason", "HILANG"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get("/api/stock/movements").with(TestAuth.as(cashier)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(stockMovementRepository.count()).isZero();
        assertThat(productRepository.findById(minyak.getId()).orElseThrow().getStock()).isEqualTo(3);
    }

    @Test
    void without_token_every_stock_endpoint_returns401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(post("/api/stock/in").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/stock/adjustment").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/stock/movements"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private ResultActions stockIn(User as, Object body) throws Exception {
        return mockMvc.perform(post("/api/stock/in").with(TestAuth.as(as))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions adjust(User as, Object body) throws Exception {
        return mockMvc.perform(post("/api/stock/adjustment").with(TestAuth.as(as))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private void clear() {
        // children first: every foreign key is ON DELETE RESTRICT
        stockMovementRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    private User user(String username, String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Product product(Category category, String sku, String name, int stock) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setCategory(category);
        product.setUnit("PCS");
        product.setCostPrice(1000);
        product.setSellPrice(2000);
        product.setStock(stock);
        product.setMinStock(0);
        return productRepository.save(product);
    }
}
