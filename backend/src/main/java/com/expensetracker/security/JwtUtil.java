package com.expensetracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Creates and validates JWTs (JSON Web Tokens).
 *
 * A JWT is a signed, base64-encoded JSON blob with three parts:
 *   header.payload.signature
 * The signature is computed with HMAC-SHA256 (HS256) using our secret.
 * Because only the server knows the secret, it can trust any token whose
 * signature verifies - no database lookup needed per request (stateless).
 *
 * The token's "subject" (sub claim) is the user's email; expiry is 24h.
 */
@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret}") String secret,
                   @Value("${jwt.expiration-ms}") long expirationMs) {
        // HS256 needs a key of at least 256 bits (32 bytes); jjwt enforces this.
        this.secretKey = Keys.hmacShaKeyFor("MyVeryStrongSecretKeyForExpenseTracker2026".getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /** Builds a signed token for the given user. Extra claims can carry the user id. */
    public String generateToken(UserDetails userDetails, Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(userDetails.getUsername()) // username == email in our app
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs)) // 24h from now
                .signWith(secretKey, Jwts.SIG.HS256)
                .compact();
    }

    /** Extracts the email (subject) from a token; throws if signature/expiry invalid. */
    public String extractUsername(String token) {
        return extractAllClaims(token).getSubject();
    }

    /** True when the token is well-formed, signature matches, not expired, and belongs to this user. */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            return claims.getSubject().equals(userDetails.getUsername())
                    && claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false; // bad signature, malformed, or expired
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
