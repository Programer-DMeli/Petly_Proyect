package com.petly.auth;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.petly.usuarios.Usuario;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Emite los JWT de Petly (US-21, Fase 3).
 * HS256 con secreto de 32+ caracteres por env SPRING_JWT_SECRET (nunca en Git).
 * Claims: sub=id, email, rol, username, iss=petly-spring, aud=petly-django, exp=1h.
 */
@Service
public class JwtService {

    private final SecretKey key;

    public JwtService(
            @Value("${spring.jwt.secret:change-me-shared-secret-for-jwt-validation-min-32-chars}")
            String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarToken(Usuario usuario) {
        Date ahora = new Date();
        Date vence = new Date(ahora.getTime() + 3600_000L); // 1 hora
        return Jwts.builder()
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getCorreo())
                .claim("rol", usuario.getRol().name())
                .claim("username", usuario.getUsername())
                .issuer("petly-spring")
                .audience().add("petly-django").and()
                .issuedAt(ahora)
                .expiration(vence)
                .signWith(key)
                .compact();
    }
}
