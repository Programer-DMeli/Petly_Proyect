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
                .claim("type", "access")
                .claim("version", usuario.getTokenVersion())
                .issuer("petly-spring")
                .audience().add("petly-django").and()
                .issuedAt(ahora)
                .expiration(vence)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Genera token de recuperaci�n de contrase�a (US-24).
     * Expira en 15 minutos, claim type=password_reset y jti unico para que
     * cada solicitud produzca un token distinto (hash unico en la tabla).
     */
    public String generarTokenPasswordReset(Usuario usuario) {
        Date ahora = new Date();
        Date vence = new Date(ahora.getTime() + 900_000L); // 15 minutos
        return Jwts.builder()
                .id(java.util.UUID.randomUUID().toString())
                .subject(String.valueOf(usuario.getId()))
                .claim("email", usuario.getCorreo())
                .claim("type", "password_reset")
                .issuer("petly-spring")
                .audience().add("petly-django").and()
                .issuedAt(ahora)
                .expiration(vence)
                .signWith(key, Jwts.SIG.HS256)
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

    /**
     * Valida token de password reset y extrae user_id (sub).
     */
    public Long validarTokenPasswordReset(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .requireIssuer("petly-spring")
            .requireAudience("petly-django")
            .build()
            .parseSignedClaims(token)
            .getPayload();

        if (!"password_reset".equals(claims.get("type"))) {
            throw new IllegalArgumentException("Token no es de recuperaci�n de contrase�a");
        }

        String sub = claims.getSubject();
        return Long.parseLong(sub);
    }
}