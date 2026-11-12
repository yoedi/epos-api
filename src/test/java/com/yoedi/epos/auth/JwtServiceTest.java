package com.yoedi.epos.auth;

import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure unit test: no Spring, no database. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes";
    private static final long EIGHT_HOURS = 28_800;
    private static final Instant NOW = Instant.parse("2026-09-16T07:00:00Z");

    private final JwtService jwtService = new JwtService(SECRET, EIGHT_HOURS, fixedClock(NOW));

    @Test
    void token_carries_sub_username_role_and_an_8_hour_exp() {
        Claims claims = Jwts.parser().verifyWith(key(SECRET)).clock(() -> Date.from(NOW)).build()
                .parseSignedClaims(jwtService.generateToken(user(2L, "rina", Role.CASHIER))).getPayload();

        assertThat(claims.getSubject()).isEqualTo("2");
        assertThat(claims.get("username", String.class)).isEqualTo("rina");
        assertThat(claims.get("role", String.class)).isEqualTo("CASHIER");
        assertThat(claims.getExpiration().toInstant()).isEqualTo(NOW.plusSeconds(EIGHT_HOURS));
    }

    @Test
    void parse_returns_the_principal_from_a_valid_token() {
        String token = jwtService.generateToken(user(1L, "budi", Role.OWNER));

        assertThat(jwtService.parse(token)).isEqualTo(new AuthenticatedUser(1L, "budi", Role.OWNER));
    }

    @Test
    void parse_accepts_a_token_just_before_it_expires() {
        String token = jwtService.generateToken(user(1L, "budi", Role.OWNER));
        JwtService later = new JwtService(SECRET, EIGHT_HOURS, fixedClock(NOW.plusSeconds(EIGHT_HOURS - 1)));

        assertThat(later.parse(token).id()).isEqualTo(1L);
    }

    @Test
    void parse_rejects_an_expired_token() {
        String token = jwtService.generateToken(user(1L, "budi", Role.OWNER));
        JwtService later = new JwtService(SECRET, EIGHT_HOURS, fixedClock(NOW.plus(Duration.ofHours(9))));

        assertThatThrownBy(() -> later.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_rejects_a_token_signed_with_another_secret() {
        JwtService other = new JwtService("another-secret-that-is-also-32-bytes-long", EIGHT_HOURS, fixedClock(NOW));
        String forged = other.generateToken(user(1L, "budi", Role.OWNER));

        assertThatThrownBy(() -> jwtService.parse(forged)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_rejects_a_tampered_payload() {
        String[] parts = jwtService.generateToken(user(2L, "rina", Role.CASHIER)).split("\\.");
        String ownerPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"2\",\"username\":\"rina\",\"role\":\"OWNER\"}".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> jwtService.parse(parts[0] + "." + ownerPayload + "." + parts[2]))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_rejects_an_unsigned_token() {
        String unsigned = Jwts.builder().subject("1").claim("username", "budi").claim("role", "OWNER")
                .expiration(Date.from(NOW.plusSeconds(60))).compact();

        assertThatThrownBy(() -> jwtService.parse(unsigned)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_rejects_garbage_and_blank_input() {
        assertThatThrownBy(() -> jwtService.parse("not-a-jwt")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwtService.parse("")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void parse_rejects_a_token_with_an_unknown_role() {
        String token = Jwts.builder().subject("1").claim("username", "x").claim("role", "ADMIN")
                .expiration(Date.from(NOW.plusSeconds(60))).signWith(key(SECRET)).compact();

        assertThatThrownBy(() -> jwtService.parse(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void constructor_rejects_a_secret_shorter_than_32_bytes() {
        assertThatThrownBy(() -> new JwtService("too-short", EIGHT_HOURS, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructor_rejects_a_non_positive_lifetime() {
        assertThatThrownBy(() -> new JwtService(SECRET, 0, Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private static Clock fixedClock(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private static User user(long id, String username, Role role) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id); // BaseEntity has no id setter
        user.setUsername(username);
        user.setRole(role);
        return user;
    }
}
