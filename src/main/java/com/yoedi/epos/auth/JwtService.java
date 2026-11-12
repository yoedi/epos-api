package com.yoedi.epos.auth;

import com.yoedi.epos.user.Role;
import com.yoedi.epos.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and verifies the stateless access token (HS256). Claims: {@code sub} (user id),
 * {@code username}, {@code role}, {@code exp}. There is no refresh token in v1.
 */
@Service
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final long expirationSeconds;
    private final Clock clock;

    @Autowired
    public JwtService(
            @Value("${epos.jwt.secret}") String secret,
            @Value("${epos.jwt.expiration-seconds}") long expirationSeconds) {
        this(secret, expirationSeconds, Clock.systemUTC());
    }

    /** Clock is injectable so tests can issue tokens that are already expired. */
    public JwtService(String secret, long expirationSeconds, Clock clock) {
        byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_SECONDS must be positive");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expirationSeconds = expirationSeconds;
        this.clock = clock;
    }

    public long expirationSeconds() {
        return expirationSeconds;
    }

    public String generateToken(User user) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(key)
                .compact();
    }

    /** @throws InvalidTokenException if the token is malformed, tampered with, expired, or incomplete */
    public AuthenticatedUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new AuthenticatedUser(
                    Long.parseLong(claims.getSubject()),
                    claims.get("username", String.class),
                    Role.valueOf(claims.get("role", String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException covers a blank token, a non-numeric sub, and an unknown role.
            throw new InvalidTokenException("Invalid JWT", e);
        }
    }
}
