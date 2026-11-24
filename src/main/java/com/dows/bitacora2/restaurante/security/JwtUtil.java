package com.dows.bitacora2.restaurante.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

/**
 * Genera y valida los JWT de la API.
 * El secreto se recibe SOLO por la variable de entorno JWT_SECRET (Base64, mínimo 256 bits)
 * y se valida al arrancar: si falta o es débil, la aplicación no inicia (fail-fast).
 */
@Slf4j
@Component
public class JwtUtil {

    static final int LONGITUD_MINIMA_BYTES = 32; // 256 bits, requerido por HMAC-SHA256

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${jwt.secret:}") String secret,
                   @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
        this.key = construirLlave(secret);
        this.expirationMs = expirationMs;
    }

    private static SecretKey construirLlave(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "Falta la variable de entorno JWT_SECRET (clave Base64 de mínimo 256 bits)");
        }
        byte[] bytes;
        try {
            bytes = Decoders.BASE64.decode(secret.strip());
        } catch (DecodingException e) {
            throw new IllegalStateException("JWT_SECRET debe estar codificado en Base64", e);
        }
        if (bytes.length < LONGITUD_MINIMA_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET es demasiado corto: se requieren mínimo 256 bits (32 bytes) y se recibieron "
                            + bytes.length * 8 + " bits");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(String email, String rol) {
        Date ahora = new Date();
        return Jwts.builder()
                .subject(email)
                .claim("rol", rol)
                .issuedAt(ahora)
                .expiration(new Date(ahora.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(key).build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Intento de acceso con JWT expirado: {}", e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Intento de acceso con JWT inválido o corrupto: {}", e.getMessage());
            return false;
        }
    }
}
