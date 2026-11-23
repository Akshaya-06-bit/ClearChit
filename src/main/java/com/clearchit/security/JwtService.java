package com.clearchit.security;

import com.clearchit.model.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey secretKey;
    private final long expiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expiration) {

        this.secretKey = buildKey(secret);

        this.expiration = expiration;
    }

    /*
     * Build the HMAC key from JWT_SECRET.
     *
     * The documented format is Base64, but a value pasted into a hosting
     * dashboard is often Base64URL or plain text. Decoding it strictly made
     * the whole application fail to start. Try each interpretation and use
     * the first one that yields at least 256 bits (32 bytes).
     */
    private static SecretKey buildKey(String secret) {

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET is empty. Set it to a Base64 string of "
                            + "at least 32 bytes.");
        }

        String trimmed = secret.trim();

        try {
            byte[] bytes = Decoders.BASE64.decode(trimmed);
            if (bytes.length >= 32) {
                return Keys.hmacShaKeyFor(bytes);
            }
        } catch (RuntimeException ignored) {
            // not standard Base64 - try the next format
        }

        try {
            byte[] bytes = Decoders.BASE64URL.decode(trimmed);
            if (bytes.length >= 32) {
                return Keys.hmacShaKeyFor(bytes);
            }
        } catch (RuntimeException ignored) {
            // not Base64URL either - use the raw text
        }

        byte[] raw = trimmed.getBytes(StandardCharsets.UTF_8);
        if (raw.length >= 32) {
            return Keys.hmacShaKeyFor(raw);
        }

        throw new IllegalStateException(
                "JWT_SECRET is too short. It must be at least 32 bytes "
                        + "(256 bits). Generate one with: openssl rand -base64 48");
    }

    public String generateToken(UserAccount userAccount) {

        return Jwts.builder()
                .subject(userAccount.getEmail())
                .claim("userId", userAccount.getId())
                .claim("memberId", userAccount.getMember().getId())
                .claim("role", userAccount.getRole().name())
                .issuedAt(new Date())
                .expiration(
                        new Date(
                                System.currentTimeMillis()
                                        + expiration))
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {

        return getClaims(token).getSubject();
    }

    public boolean isTokenValid(String token) {

        try {
            getClaims(token);
            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private Claims getClaims(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}