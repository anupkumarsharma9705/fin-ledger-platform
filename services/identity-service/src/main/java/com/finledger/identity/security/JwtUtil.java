package com.finledger.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Component
public class JwtUtil {

    private final JwtProperties jwtProperties;

    public JwtUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateAccessToken(String subject) {
        return generateToken(subject, jwtProperties.getAccessTokenExpirationSeconds(), "access");
    }

    public String generateRefreshToken(String subject) {
        return generateToken(subject, jwtProperties.getRefreshTokenExpirationSeconds(), "refresh");
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractType(String token) {
        Object type = parseClaims(token).get("type");
        return type == null ? "" : type.toString();
    }

    public boolean isTokenValid(String token) {
        Claims claims = parseClaims(token);
        return claims.getExpiration().after(new Date());
    }

    private String generateToken(String subject, long expirationSeconds, String type) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(subject)
                .claim("type", type)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds)))
                .signWith(getSigningKey())
                .compact();
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
