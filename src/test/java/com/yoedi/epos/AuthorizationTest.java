package com.yoedi.epos;

import com.yoedi.epos.auth.JwtService;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Per-role access rules from "Access rights summary" in docs/api-spec.md, checked through the
 * real security filter chain with real JWTs (not mocked users), for category, product, and sale.
 *
 * <p>Each endpoint is written as {@code "METHOD /path"}; {@code {category}}, {@code {emptyCategory}},
 * {@code {product}}, and {@code {barcode}} are replaced with ids of rows seeded in {@link #setUp()}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthorizationTest {

    private static final String BARCODE = "8991111222333";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtService jwtService;

    private String ownerToken;
    private String cashierToken;
    private long categoryId;
    private long emptyCategoryId;
    private long productId;

    @BeforeEach
    void setUp() {
        clear();
        ownerToken = jwtService.generateToken(user("budi", "Budi Santoso", Role.OWNER));
        cashierToken = jwtService.generateToken(user("rina", "Rina Lestari", Role.CASHIER));
        categoryId = insertCategory("Sembako");
        emptyCategoryId = insertCategory("Kosong");
        productId = jdbc.queryForObject("""
                insert into products (sku, barcode, name, category_id, unit, cost_price, sell_price, stock, min_stock)
                values ('BRS-005', ?, 'Beras Premium 5 kg', ?, 'PCS', 65000, 78000, 24, 5)
                returning id
                """, Long.class, BARCODE, categoryId);
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    // ---- endpoints open to both roles --------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "GET /api/categories",
            "GET /api/products",
            "GET /api/products/{product}",
            "GET /api/products/barcode/{barcode}",
            "POST /api/sales",
            "GET /api/sales",
            "GET /api/store-settings",
    })
    void both_roles_are_allowed(String endpoint) throws Exception {
        int expected = endpoint.startsWith("POST") ? 201 : 200;

        assertThat(statusOf(endpoint, ownerToken)).as("OWNER").isEqualTo(expected);
        assertThat(statusOf(endpoint, cashierToken)).as("CASHIER").isEqualTo(expected);
    }

    // ---- OWNER-only endpoints ----------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "POST /api/categories",
            "PATCH /api/categories/{category}",
            "DELETE /api/categories/{emptyCategory}",
            "POST /api/products",
            "PATCH /api/products/{product}",
            "PATCH /api/products/{product}/status",
            "POST /api/stock/in",
            "POST /api/stock/adjustment",
            "GET /api/stock/movements",
            "PUT /api/store-settings",
    })
    void owner_only_endpoints_allow_owner_and_forbid_cashier(String endpoint) throws Exception {
        int expectedForOwner = endpoint.startsWith("POST") ? 201 : endpoint.startsWith("DELETE") ? 204 : 200;

        assertThat(statusOf(endpoint, ownerToken)).as("OWNER").isEqualTo(expectedForOwner);

        // a forbidden call must not have a side effect: re-seed what the owner call changed
        setUp();
        send(endpoint, cashierToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void forbidden_cashier_write_does_not_change_data() throws Exception {
        send("PATCH /api/products/{product}/status", cashierToken).andExpect(status().isForbidden());
        send("DELETE /api/categories/{emptyCategory}", cashierToken).andExpect(status().isForbidden());

        assertThat(jdbc.queryForObject("select is_active from products where id = ?", Boolean.class, productId))
                .isTrue();
        assertThat(jdbc.queryForObject("select count(*) from categories where id = ?", Integer.class,
                emptyCategoryId)).isEqualTo(1);
    }

    // ---- no / bad token ----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {
            "GET /api/categories",
            "POST /api/categories",
            "PATCH /api/categories/{category}",
            "DELETE /api/categories/{emptyCategory}",
            "GET /api/products",
            "GET /api/products/{product}",
            "GET /api/products/barcode/{barcode}",
            "POST /api/products",
            "PATCH /api/products/{product}",
            "PATCH /api/products/{product}/status",
            "POST /api/sales",
            "GET /api/sales",
            "POST /api/stock/in",
            "POST /api/stock/adjustment",
            "GET /api/stock/movements",
            "GET /api/store-settings",
            "PUT /api/store-settings",
    })
    void without_token_returns401_UNAUTHORIZED(String endpoint) throws Exception {
        send(endpoint, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void garbage_token_returns401_UNAUTHORIZED() throws Exception {
        send("GET /api/products", "not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ---- cost_price visibility ---------------------------------------------------------------

    @Test
    void cashier_never_sees_cost_price() throws Exception {
        send("GET /api/products", cashierToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sell_price").value(78000))
                .andExpect(jsonPath("$.content[0].cost_price").doesNotExist());
        send("GET /api/products/{product}", cashierToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost_price").doesNotExist());
        send("GET /api/products/barcode/{barcode}", cashierToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost_price").doesNotExist());
    }

    @Test
    void owner_sees_cost_price() throws Exception {
        send("GET /api/products", ownerToken)
                .andExpect(jsonPath("$.content[0].cost_price").value(65000));
        send("GET /api/products/{product}", ownerToken)
                .andExpect(jsonPath("$.cost_price").value(65000));
        send("GET /api/products/barcode/{barcode}", ownerToken)
                .andExpect(jsonPath("$.cost_price").value(65000));
    }

    // ---- helpers -----------------------------------------------------------------------------

    private int statusOf(String endpoint, String token) throws Exception {
        return send(endpoint, token).andReturn().getResponse().getStatus();
    }

    private ResultActions send(String endpoint, String token)
            throws Exception {
        String[] parts = endpoint.split(" ", 2);
        String path = parts[1]
                .replace("{category}", String.valueOf(categoryId))
                .replace("{emptyCategory}", String.valueOf(emptyCategoryId))
                .replace("{product}", String.valueOf(productId))
                .replace("{barcode}", BARCODE);

        MockHttpServletRequestBuilder request =
                MockMvcRequestBuilders.request(HttpMethod.valueOf(parts[0]), path);
        String body = bodyFor(endpoint);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        if (token != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    /** A valid body per endpoint, so an allowed role gets a 2xx and not a validation error. */
    private String bodyFor(String endpoint) {
        return switch (endpoint) {
            case "POST /api/categories" -> "{\"name\": \"Minuman\"}";
            case "PATCH /api/categories/{category}" -> "{\"name\": \"Sembako Baru\"}";
            case "POST /api/products" -> """
                    {"sku": "KCP-001", "name": "Kecap Manis 275 ml", "category_id": %d, "unit": "PCS",
                     "cost_price": 9500, "sell_price": 11500, "initial_stock": 24, "min_stock": 10}
                    """.formatted(categoryId);
            case "PATCH /api/products/{product}" -> "{\"sell_price\": 80000}";
            case "PATCH /api/products/{product}/status" -> "{\"is_active\": false}";
            case "POST /api/stock/in" -> """
                    {"items": [{"product_id": %d, "qty": 5}]}
                    """.formatted(productId);
            case "POST /api/stock/adjustment" -> """
                    {"product_id": %d, "physical_stock": 20, "reason": "RUSAK"}
                    """.formatted(productId);
            case "PUT /api/store-settings" -> "{\"store_name\": \"Toko Berkah Jaya\"}";
            case "POST /api/sales" -> """
                    {"items": [{"product_id": %d, "qty": 1}], "payment_method": "CASH", "paid": 100000}
                    """.formatted(productId);
            default -> null;
        };
    }

    private User user(String username, String name, Role role) {
        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(role);
        return userRepository.save(user);
    }

    private long insertCategory(String name) {
        return jdbc.queryForObject("insert into categories (name) values (?) returning id", Long.class, name);
    }

    /** Children first: every foreign key is ON DELETE RESTRICT. */
    private void clear() {
        jdbc.update("delete from stock_movements");
        jdbc.update("delete from sale_items");
        jdbc.update("delete from sales");
        jdbc.update("delete from products");
        jdbc.update("delete from categories");
        jdbc.update("delete from users");
    }
}
