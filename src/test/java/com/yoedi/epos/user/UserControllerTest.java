package com.yoedi.epos.user;

import com.yoedi.epos.TestAuth;
import com.yoedi.epos.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private User owner;
    private User cashier;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        owner = save("Budi Santoso", "budi", Role.OWNER, true);
        save("Rina Lestari", "rina", Role.CASHIER, true);
        save("Dewi Anggraini", "dewi", Role.CASHIER, false);
        cashier = userRepository.findByUsername("rina").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    private User save(String name, String username, Role role, boolean active) {
        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setPasswordHash("$2a$10$hash");
        user.setRole(role);
        user.setActive(active);
        return userRepository.save(user);
    }

    @Test
    void asOwner_returnsAllUsersSortedByName_includingInactive_withoutPasswordHash() throws Exception {
        mockMvc.perform(get("/api/users").with(TestAuth.as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("Budi Santoso"))
                .andExpect(jsonPath("$[1].name").value("Dewi Anggraini"))
                .andExpect(jsonPath("$[1].is_active").value(false))
                .andExpect(jsonPath("$[2].name").value("Rina Lestari"))
                .andExpect(jsonPath("$[2].username").value("rina"))
                .andExpect(jsonPath("$[2].role").value("CASHIER"))
                .andExpect(jsonPath("$[2].is_active").value(true))
                .andExpect(jsonPath("$[0].password_hash").doesNotExist());
    }

    @Test
    void roleFilter_returnsOnlyThatRole() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "CASHIER").with(TestAuth.as(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Dewi Anggraini"))
                .andExpect(jsonPath("$[1].name").value("Rina Lestari"));
    }

    @Test
    void invalidRole_returns400() throws Exception {
        mockMvc.perform(get("/api/users").param("role", "ADMIN").with(TestAuth.as(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void asCashier_returns403_with_code() throws Exception {
        mockMvc.perform(get("/api/users").with(TestAuth.as(cashier)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }
}
