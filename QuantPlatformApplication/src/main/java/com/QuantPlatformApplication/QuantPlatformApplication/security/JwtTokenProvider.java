package com.QuantPlatformApplication.QuantPlatformApplication.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * JWT Token Provider — generates and validates HS256-signed JSON Web Tokens.

 * How it works:
 1. On login/register, we call generateToken(email) → returns a signed JWT string
 2. On every request, the JwtAuthenticationFilter extracts the token from the Authorization header, then calls validateToken() + getEmailFromToken()
 3. The signing key comes from application.yml → jwt.secret (Base64-encoded)
 */

@Component
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMs) {

        // Decode the Base64 secret into an HMAC-SHA signing key
        this.signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMs = expirationMs;
    }

    // ── Generate Token ───────────────────────────────────────────────────────

    /**
     * Create a JWT with the user's email as the subject.
     * Token contains: sub (email), iat (issued-at), exp (expiration).
     */
    public String generateToken(String email) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // ── Extract Claims ──────────────────────────────────────────────────────

    /**
     * Parse the JWT and return the email (subject) stored inside.
     */
    public String getEmailFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    // ── Validate Token ──────────────────────────────────────────────────────

    /**
     * Returns true if the token is structurally valid, properly signed,
     * and not yet expired.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            // Covers: ExpiredJwt, MalformedJwt, UnsupportedJwt, SignatureException
            return false;
        }
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
