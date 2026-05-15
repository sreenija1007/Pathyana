package com.pathyana.api.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT creation and validation.
 *
 * Design notes:
 *  - HS256 signing with a 256-bit secret. Symmetric (same key signs and verifies).
 *    Simple and fast for single-service setups. If we ever split into microservices
 *    we'll switch to RS256 (asymmetric).
 *  - Token claims:
 *      sub:   user ID (canonical identifier — never the email, which can change)
 *      email: convenience for the frontend
 *      iss:   issuer (lets us reject tokens minted by other services)
 *      iat:   issued-at (for token age checks)
 *      exp:   expiration (jjwt validates this automatically)
 *  - We don't store tokens server-side. JWT is stateless by design. Trade-off: we can't
 *    revoke a token before it expires. If we need revocation later (compromised account,
 *    logout), we add a tokenVersion field to User and bump it; tokens with old versions
 *    become invalid.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    @Value("${pathyana.jwt.secret}")
    private String secret;

    @Value("${pathyana.jwt.expiration-ms}")
    private long expirationMs;

    @Value("${pathyana.jwt.issuer}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "PATHYANA_JWT_SECRET environment variable is not set. " +
                "Generate one: openssl rand -base64 64");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                "PATHYANA_JWT_SECRET must be at least 32 characters (256 bits) for HS256. " +
                "Got " + secret.length() + " characters.");
        }
        // jjwt expects a SecretKey, not a raw string.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        log.info("JWT service initialized. Issuer={}, expiration={}ms", issuer, expirationMs);
    }

    /**
     * Mint a new token for a logged-in user.
     */
    public String generateToken(Long userId, String email) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", email);

        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .claims(claims)
                .subject(String.valueOf(userId))
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parse and validate. Returns claims if valid, throws if not (expired, tampered, wrong issuer).
     * The filter catches the exception and returns 401.
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        return Long.parseLong(parseToken(token).getSubject());
    }

    public String extractEmail(String token) {
        return parseToken(token).get("email", String.class);
    }

    public long getExpirationSeconds() {
        return expirationMs / 1000;
    }
}
