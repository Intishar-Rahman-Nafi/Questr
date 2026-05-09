package com.app.questr.security;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
@Component
@Slf4j
public class JwtTokenProvider {
    private final String jwtSecret;
    private final long expirationMs;
    private final long refreshExpirationMs;
    private SecretKey signingKey;
    public JwtTokenProvider(
            @Value("${jwt.secret}") String jwtSecret,
            @Value("${jwt.expiration}") long expirationMs,
            @Value("${jwt.refresh-expiration}") long refreshExpirationMs) {
        this.jwtSecret=jwtSecret; this.expirationMs=expirationMs; this.refreshExpirationMs=refreshExpirationMs;
    }
    @PostConstruct
    public void init() {
        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
    public String generateAccessToken(UserPrincipal p) { return buildToken(p, expirationMs, "ACCESS"); }
    public String generateRefreshToken(UserPrincipal p) { return buildToken(p, refreshExpirationMs, "REFRESH"); }
    private String buildToken(UserPrincipal p, long ttlMs, String type) {
        Date now = new Date();
        return Jwts.builder()
                .subject(p.getId().toString())
                .claim("username", p.getUsername())
                .claim("email", p.getEmail())
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMs))
                .signWith(signingKey)
                .compact();
    }
    public boolean validateAccessToken(String token) {
        try { Claims c = parseClaims(token); return "ACCESS".equals(c.get("type", String.class)); }
        catch (JwtException | IllegalArgumentException e) { log.debug("Invalid access token: {}", e.getMessage()); return false; }
    }
    public boolean validateToken(String token) {
        try { parseClaims(token); return true; }
        catch (JwtException | IllegalArgumentException e) { log.debug("Invalid token: {}", e.getMessage()); return false; }
    }
    public UUID getUserIdFromToken(String token) { return UUID.fromString(parseClaims(token).getSubject()); }
    public String getTokenType(String token) { return parseClaims(token).get("type", String.class); }
    public long getExpirationMs() { return expirationMs; }
    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
