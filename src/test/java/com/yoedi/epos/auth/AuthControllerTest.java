package com.yoedi.epos.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoedi.epos.TestcontainersConfiguration;
import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import com.yoedi.epos.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AuthControllerTest {

    private static final String PASSWORD = "rahasia123";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Value("${epos.jwt.secret}")
    private String jwtSecret;

    private User cashier;
    private User owner;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        cashier = saveUser("Rina Lestari", "rina", Role.CASHIER, true);
        owner = saveUser("Budi Santoso", "budi", Role.OWNER, true);
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteAll();
    }

    // --- POST /auth/login -------------------------------------------------------------------

    @Test
    void login_returns200_with_the_contract_shape_and_never_the_password_hash() throws Exception {
        login("rina", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isString())
                .andExpect(jsonPath("$.token_type").value("Bearer"))
                .andExpect(jsonPath("$.expires_in").value(28800))
                .andExpect(jsonPath("$.user.id").value(cashier.getId()))
                .andExpect(jsonPath("$.user.name").value("Rina Lestari"))
                .andExpect(jsonPath("$.user.username").value("rina"))
                .andExpect(jsonPath("$.user.role").value("CASHIER"))
                .andExpect(jsonPath("$.user.password_hash").doesNotExist())
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void login_token_is_accepted_by_a_protected_endpoint() throws Exception {
        String token = tokenFor("budi", PASSWORD);

        mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void login_with_wrong_password_returns401_INVALID_CREDENTIALS() throws Exception {
        login("rina", "salah-total")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.title").value("Login gagal"))
                .andExpect(jsonPath("$.detail").value("Username atau password salah"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void login_with_unknown_username_returns_the_same_error_as_a_wrong_password() throws Exception {
        login("tidak-ada", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Username atau password salah"));
    }

    @Test
    void login_with_inactive_user_returns_INVALID_CREDENTIALS_not_a_distinct_error() throws Exception {
        saveUser("Dewi", "dewi", Role.CASHIER, false);

        login("dewi", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Username atau password salah"));
    }

    @Test
    void login_with_blank_fields_returns400_VALIDATION_ERROR_with_field_errors() throws Exception {
        login("", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'username')].message").value("Username wajib diisi"))
                .andExpect(jsonPath("$.errors[?(@.field == 'password')].message").value("Password wajib diisi"));
    }

    @Test
    void login_with_missing_body_returns400_VALIDATION_ERROR() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // --- GET /auth/me -----------------------------------------------------------------------

    @Test
    void me_returns_the_user_of_the_token() throws Exception {
        me(tokenFor("rina", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(cashier.getId()))
                .andExpect(jsonPath("$.name").value("Rina Lestari"))
                .andExpect(jsonPath("$.username").value("rina"))
                .andExpect(jsonPath("$.role").value("CASHIER"))
                .andExpect(jsonPath("$.password_hash").doesNotExist());
    }

    @Test
    void me_without_token_returns401_UNAUTHORIZED_in_the_error_format() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.title").value("Sesi tidak valid"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void me_with_garbage_token_returns401_UNAUTHORIZED() throws Exception {
        me("not-a-jwt")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_with_non_bearer_scheme_returns401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Basic cmluYTpyYWhhc2lhMTIz"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_with_expired_token_returns401_UNAUTHORIZED() throws Exception {
        Clock issuedLongAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(9)), java.time.ZoneOffset.UTC);
        String expired = new JwtService(jwtSecret, 28_800, issuedLongAgo).generateToken(cashier);

        me(expired)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_with_token_signed_by_another_secret_returns401_UNAUTHORIZED() throws Exception {
        String forged = new JwtService("another-secret-that-is-also-32-bytes-long", 28_800, Clock.systemUTC())
                .generateToken(owner);

        me(forged)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_for_a_user_deactivated_after_login_returns401_UNAUTHORIZED() throws Exception {
        String token = tokenFor("rina", PASSWORD);
        cashier.setActive(false);
        userRepository.save(cashier);

        me(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_for_a_deleted_user_returns401_UNAUTHORIZED() throws Exception {
        String token = jwtService.generateToken(cashier);
        userRepository.delete(cashier);

        me(token)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // --- Route protection and roles ---------------------------------------------------------

    @Test
    void protected_endpoints_without_token_return401() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/sales").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void owner_only_endpoint_returns403_FORBIDDEN_for_a_cashier_token_and_passes_for_an_owner() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Snack"));

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("rina", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        mockMvc.perform(post("/api/categories")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenFor("budi", PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    @Test
    void login_swagger_and_health_are_public() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    // --- helpers ----------------------------------------------------------------------------

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private String tokenFor(String username, String password) throws Exception {
        String json = login(username, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).get("access_token").asText();
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private User saveUser(String name, String username, Role role, boolean active) {
        User user = new User();
        user.setName(name);
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setRole(role);
        user.setActive(active);
        return userRepository.save(user);
    }
}
