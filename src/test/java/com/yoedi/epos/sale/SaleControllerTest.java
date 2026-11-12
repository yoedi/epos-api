package com.yoedi.epos.sale;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoedi.epos.TestAuth;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.Category;
import com.yoedi.epos.product.Product;
import com.yoedi.epos.user.User;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, SaleTestData.class})
class SaleControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SaleTestData data;

    private User cashier;
    private Product beras;
    private Product minyak;

    @BeforeEach
    void setUp() {
        data.clear();
        cashier = data.cashier();
        Category category = data.category();
        beras = data.product(category, "BRS-005", "Beras Premium 5 kg", 78000, 24);
        minyak = data.product(category, "MYK-002", "Minyak Goreng 2 L", 36500, 3);
    }

    @AfterEach
    void tearDown() {
        data.clear();
    }

    @Test
    void checkout_returns201_with_the_contract_shape() throws Exception {
        checkout(body(List.of(item(beras.getId(), 1), item(minyak.getId(), 2)), "CASH", 160000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.invoice_no").value(org.hamcrest.Matchers.matchesPattern("INV-\\d{8}-0001")))
                .andExpect(jsonPath("$.cashier.id").value(cashier.getId()))
                .andExpect(jsonPath("$.cashier.name").value("Rina Lestari"))
                .andExpect(jsonPath("$.items[0].product_id").value(beras.getId()))
                .andExpect(jsonPath("$.items[0].product_name").value("Beras Premium 5 kg"))
                .andExpect(jsonPath("$.items[0].unit").value("PCS"))
                .andExpect(jsonPath("$.items[0].price").value(78000))
                .andExpect(jsonPath("$.items[0].qty").value(1))
                .andExpect(jsonPath("$.items[0].subtotal").value(78000))
                .andExpect(jsonPath("$.items[1].subtotal").value(73000))
                .andExpect(jsonPath("$.subtotal").value(151000))
                .andExpect(jsonPath("$.discount").value(0))
                .andExpect(jsonPath("$.total").value(151000))
                .andExpect(jsonPath("$.payment_method").value("CASH"))
                .andExpect(jsonPath("$.paid").value(160000))
                .andExpect(jsonPath("$.change_amount").value(9000))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.created_at").value(org.hamcrest.Matchers.endsWith("+07:00")));
    }

    @Test
    void checkout_with_empty_items_returns422_EMPTY_CART() throws Exception {
        checkout(body(List.of(), "CASH", 1000))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMPTY_CART"))
                .andExpect(jsonPath("$.detail").value("Keranjang masih kosong"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void checkout_with_insufficient_stock_returns422_INSUFFICIENT_STOCK() throws Exception {
        checkout(body(List.of(item(minyak.getId(), 4)), "CASH", 500000))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.title").value("Stok tidak mencukupi"))
                .andExpect(jsonPath("$.detail").value("Stok Minyak Goreng 2 L tersisa 3"));
    }

    @Test
    void checkout_with_insufficient_payment_returns422_INSUFFICIENT_PAYMENT() throws Exception {
        checkout(body(List.of(item(beras.getId(), 1)), "CASH", 33500))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_PAYMENT"))
                .andExpect(jsonPath("$.detail").value("Uang kurang Rp 44.500"));
    }

    @Test
    void checkout_with_inactive_product_returns422_PRODUCT_INACTIVE() throws Exception {
        beras.setActive(false);
        data.productRepository.save(beras);

        checkout(body(List.of(item(beras.getId(), 1)), "CASH", 100000))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PRODUCT_INACTIVE"))
                .andExpect(jsonPath("$.detail").value("Produk Beras Premium 5 kg sudah tidak aktif"));
    }

    @Test
    void checkout_with_unknown_product_returns404_NOT_FOUND() throws Exception {
        checkout(body(List.of(item(999_999L, 1)), "CASH", 100000))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Produk tidak ditemukan"));
    }

    @Test
    void checkout_with_invalid_body_returns400_VALIDATION_ERROR_with_field_errors() throws Exception {
        checkout(Map.of("items", List.of(Map.of("product_id", beras.getId(), "qty", 0)), "paid", -1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'items[0].qty')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'payment_method')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'paid')]").exists());
    }

    @Test
    void checkout_with_unsupported_payment_method_returns400_VALIDATION_ERROR() throws Exception {
        checkout(body(List.of(item(beras.getId(), 1)), "QRIS", 100000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void checkout_without_token_returns401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                body(List.of(item(beras.getId(), 1)), "CASH", 100000))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void checkout_takes_the_cashier_from_the_token_and_ignores_a_spoofed_header() throws Exception {
        mockMvc.perform(post("/api/sales")
                        .with(TestAuth.as(cashier))
                        .header("X-Cashier-Id", 999_999)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                body(List.of(item(beras.getId(), 1)), "CASH", 100000))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cashier.id").value(cashier.getId()));
    }

    private ResultActions checkout(Object body) throws Exception {
        return mockMvc.perform(post("/api/sales")
                .with(TestAuth.as(cashier))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private static Map<String, Object> item(long productId, int qty) {
        return Map.of("product_id", productId, "qty", qty);
    }

    private static Map<String, Object> body(List<Map<String, Object>> items, String paymentMethod, long paid) {
        return Map.of("items", items, "payment_method", paymentMethod, "paid", paid);
    }
}
