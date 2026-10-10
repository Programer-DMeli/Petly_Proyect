package com.petly.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.petly.usuarios.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite los JWT de Petly (US-21, Fase 3).
 * HS256 con secreto de 32+ caracteres por env JWT_SECRET (nunca en Git).
 * Claims: sub=id, email, rol, iss=petly-spring, aud=petly-django, exp=1h.
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(
            @Value("${JWT_SECRET:petly-dev-secret-local-de-32-caracteres-minimo}")
            String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date vence = new Date(ahora.getTime() + 3600_000L);
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getCorreo())
                .claim("rol", usuario.getRol())
                .issuer("petly-spring")
                .audience().add("petly-django").and()
                .issuedAt(ahora)
                .expiration(vence)
                .signWith(key)
                .compact();
    }

    public Claims validarToken(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .requireIssuer("petly-spring")
            .requireAudience("petly-django")
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
