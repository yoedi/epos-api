package com.yoedi.epos.store;

import com.yoedi.epos.TestcontainersConfiguration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class StoreSettingsControllerTest {

    private static final String VALID_BODY = """
            {
              "store_name": "Toko Berkah Jaya",
              "address": "Jl. Kaliurang Km 5, Yogyakarta",
              "phone": "0274-123456",
              "receipt_footer": "Terima kasih, selamat belanja kembali"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetRow() {
        jdbc.update("""
                update store_settings
                   set store_name = 'Toko Saya', address = null, phone = null,
                       receipt_footer = 'Terima kasih, selamat belanja kembali'
                 where id = 1
                """);
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void get_asCashier_returns200_with_snake_case_fields() throws Exception {
        mockMvc.perform(get("/api/store-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store_name").value("Toko Saya"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.receipt_footer").value("Terima kasih, selamat belanja kembali"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_asOwner_returns200_and_persists() throws Exception {
        mockMvc.perform(put("/api/store-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store_name").value("Toko Berkah Jaya"))
                .andExpect(jsonPath("$.address").value("Jl. Kaliurang Km 5, Yogyakarta"))
                .andExpect(jsonPath("$.phone").value("0274-123456"));

        mockMvc.perform(get("/api/store-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store_name").value("Toko Berkah Jaya"));
        assertThat(jdbc.queryForObject("select count(*) from store_settings", Integer.class)).isEqualTo(1);
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_blankOptionalFields_areStoredAsNull() throws Exception {
        mockMvc.perform(put("/api/store-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "store_name": "  Toko Baru  ", "address": "  ", "phone": "", "receipt_footer": null }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store_name").value("Toko Baru"))
                .andExpect(jsonPath("$.address").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.receipt_footer").doesNotExist());
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_blankStoreName_returns400_with_code() throws Exception {
        mockMvc.perform(put("/api/store-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"store_name\": \"  \" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("store_name"));
    }

    @Test
    @WithMockUser(roles = "OWNER")
    void put_tooLongPhone_returns400() throws Exception {
        mockMvc.perform(put("/api/store-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"store_name\": \"Toko\", \"phone\": \"" + "1".repeat(31) + "\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "CASHIER")
    void put_asCashier_returns403_with_code() throws Exception {
        mockMvc.perform(put("/api/store-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void get_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/store-settings"))
                .andExpect(status().isUnauthorized());
    }
}
