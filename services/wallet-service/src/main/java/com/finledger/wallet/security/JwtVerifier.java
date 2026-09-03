package com.finledger.wallet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtVerifier {

    private final JwtProperties jwtProperties;

    public JwtVerifier(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String extractSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isAccessTokenValid(String token) {
        Claims claims = parseClaims(token);
        String type = claims.get("type", String.class);
        return "access".equals(type) && claims.getExpiration().after(new Date());
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
