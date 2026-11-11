package com.yoedi.epos.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.category.dto.CategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanUp() {
        jdbc.update("delete from products");
        categoryRepository.deleteAll();
    }

    @Test
    void create_returns201_with_category_object() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Kebutuhan Rumah"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Kebutuhan Rumah"))
                .andExpect(jsonPath("$.product_count").value(0));
    }

    @Test
    void create_duplicateName_returns409_with_code() throws Exception {
        categoryRepository.save(category("Sembako"));

        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("sembako"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void create_blankName_returns400_with_code() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("  "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    void find_returns200_with_product_count() throws Exception {
        categoryRepository.save(category("Sembako"));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Sembako"))
                .andExpect(jsonPath("$[0].product_count").value(0));
    }

    @Test
    void update_returns200_with_new_name() throws Exception {
        Category existing = categoryRepository.save(category("Sembako"));

        mockMvc.perform(patch("/api/categories/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Sembako Baru"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId()))
                .andExpect(jsonPath("$.name").value("Sembako Baru"));
    }

    @Test
    void update_notFound_returns404_with_code() throws Exception {
        mockMvc.perform(patch("/api/categories/{id}", 999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("X"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void delete_withoutProducts_returns204() throws Exception {
        Category existing = categoryRepository.save(category("Sembako"));

        mockMvc.perform(delete("/api/categories/{id}", existing.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404_with_code() throws Exception {
        mockMvc.perform(delete("/api/categories/{id}", 999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void find_returns_product_count_and_sorts_by_name() throws Exception {
        Category sembako = categoryRepository.save(category("sembako"));
        categoryRepository.save(category("Minuman"));
        insertProduct(sembako.getId(), "SKU-1");
        insertProduct(sembako.getId(), "SKU-2");

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Minuman"))
                .andExpect(jsonPath("$[0].product_count").value(0))
                .andExpect(jsonPath("$[1].name").value("sembako"))
                .andExpect(jsonPath("$[1].product_count").value(2));
    }

    @Test
    void create_trimsName() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("  Snack  "))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Snack"));
    }

    @Test
    void create_nameTooLong_returns400() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("x".repeat(101)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void update_duplicateOfOtherCategory_returns409() throws Exception {
        categoryRepository.save(category("Sembako"));
        Category other = categoryRepository.save(category("Minuman"));

        mockMvc.perform(patch("/api/categories/{id}", other.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("SEMBAKO"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY"));
    }

    @Test
    void update_changeOnlyLetterCaseOfOwnName_returns200() throws Exception {
        Category existing = categoryRepository.save(category("sembako"));

        mockMvc.perform(patch("/api/categories/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Sembako"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sembako"));
    }

    @Test
    void delete_withProducts_returns409_with_code_and_count() throws Exception {
        Category existing = categoryRepository.save(category("Sembako"));
        insertProduct(existing.getId(), "SKU-1");
        insertProduct(existing.getId(), "SKU-2");

        mockMvc.perform(delete("/api/categories/{id}", existing.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"))
                .andExpect(jsonPath("$.detail").value("Kategori Sembako masih memiliki 2 produk"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void write_asCashier_returns403_but_list_is_allowed() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Snack"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(delete("/api/categories/{id}", 1L)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
    }

    private void insertProduct(long categoryId, String sku) {
        jdbc.update("insert into products (sku, name, category_id, unit, cost_price, sell_price) "
                + "values (?, ?, ?, 'PCS', 1000, 1500)", sku, "Produk " + sku, categoryId);
    }

    private static Category category(String name) {
        Category category = new Category();
        category.setName(name);
        return category;
    }
}
