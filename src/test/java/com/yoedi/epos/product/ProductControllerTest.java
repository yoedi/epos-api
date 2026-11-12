package com.yoedi.epos.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.Category;
import com.yoedi.epos.category.CategoryRepository;
import com.yoedi.epos.product.dto.CreateProductRequest;
import com.yoedi.epos.product.dto.UpdateStatusRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(roles = "OWNER")
@Import(TestcontainersConfiguration.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void cleanUp() {
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void create_returns201_with_product_object() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(category.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.sku").value("KCP-001"))
                .andExpect(jsonPath("$.name").value("Kecap Manis 275 ml"))
                .andExpect(jsonPath("$.category.id").value(category.getId()))
                .andExpect(jsonPath("$.unit").value("PCS"))
                .andExpect(jsonPath("$.cost_price").value(9500))
                .andExpect(jsonPath("$.sell_price").value(11500))
                .andExpect(jsonPath("$.stock").value(24))
                .andExpect(jsonPath("$.min_stock").value(10))
                .andExpect(jsonPath("$.stock_status").value("SAFE"))
                .andExpect(jsonPath("$.is_active").value(true));
    }

    @Test
    void create_duplicateSku_returns409_with_code() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        productRepository.save(product("KCP-001", "8991111222333", category));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(category.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test
    void create_duplicateBarcode_returns409_with_code() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        productRepository.save(product("KCP-999", "8991111222333", category));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(category.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_BARCODE"));
    }

    @Test
    void create_missingCategory_returns404_with_code() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest(999L))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void create_blankSku_returns400_with_code() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        CreateProductRequest request = createRequest(category.getId());
        CreateProductRequest blank = new CreateProductRequest("  ", request.barcode(), request.name(),
                request.categoryId(), request.unit(), request.costPrice(), request.sellPrice(),
                request.initialStock(), request.minStock());

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(blank)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sku"));
    }

    @Test
    void get_returns200_and_stockStatusComputed() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        Product saved = productRepository.save(product("MYK-002", "8991234567890", category, 3, 12));

        mockMvc.perform(get("/api/products/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stock_status").value("LOW_STOCK"));
    }

    @Test
    void getByBarcode_returns200() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        productRepository.save(product("MYK-002", "8991234567890", category));

        mockMvc.perform(get("/api/products/barcode/8991234567890"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("MYK-002"));
    }

    @Test
    void getByBarcode_notFound_returns404_with_code() throws Exception {
        mockMvc.perform(get("/api/products/barcode/8990000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateStatus_returns200_with_is_active() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        Product saved = productRepository.save(product("MYK-002", "8991234567890", category));

        mockMvc.perform(patch("/api/products/{id}/status", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateStatusRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_active").value(false));
    }

    @Test
    void updateStatus_acceptsIsActiveFieldFromContract() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        Product saved = productRepository.save(product("MYK-002", "8991234567890", category));

        mockMvc.perform(patch("/api/products/{id}/status", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"is_active\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.is_active").value(false))
                .andExpect(jsonPath("$.active").doesNotExist());
    }

    @Test
    void update_partialBody_changesOnlySentFields() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        Product saved = productRepository.save(product("MYK-002", "8991234567890", category));

        mockMvc.perform(patch("/api/products/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sell_price\": 37000, \"min_stock\": 15}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sell_price").value(37000))
                .andExpect(jsonPath("$.min_stock").value(15))
                .andExpect(jsonPath("$.sku").value("MYK-002"))
                .andExpect(jsonPath("$.name").value(saved.getName()));
    }

    @Test
    void update_blankSku_returns400_with_code() throws Exception {
        Category category = categoryRepository.save(category("Sembako"));
        Product saved = productRepository.save(product("MYK-002", "8991234567890", category));

        mockMvc.perform(patch("/api/products/{id}", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sku\": \"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sku"));
    }

    private CreateProductRequest createRequest(Long categoryId) {
        return new CreateProductRequest("KCP-001", "8991111222333", "Kecap Manis 275 ml",
                categoryId, "PCS", 9500L, 11500L, 24, 10);
    }

    private static Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }

    private static Product product(String sku, String barcode, Category category) {
        return product(sku, barcode, category, 100, 10);
    }

    private static Product product(String sku, String barcode, Category category, int stock, int minStock) {
        Product product = new Product();
        product.setSku(sku);
        product.setBarcode(barcode);
        product.setName("Test Product");
        product.setCategory(category);
        product.setUnit("PCS");
        product.setCostPrice(1000L);
        product.setSellPrice(2000L);
        product.setStock(stock);
        product.setMinStock(minStock);
        product.setActive(true);
        return product;
    }
}
