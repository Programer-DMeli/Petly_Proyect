package com.petly.security;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.petly.auth.JwtService;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Lee el JWT de la cabecera Authorization y deja al usuario autenticado (US-21).
 * Sin cabecera sigue igual (Spring rechaza el recurso privado).
 * Token inválido o vencido: responde 401 y no deja pasar.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String cabecera = request.getHeader("Authorization");
        if (cabecera == null || !cabecera.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = cabecera.substring(7);
        try {
            Claims claims = jwtService.validarToken(token);
            var autoridad = new SimpleGrantedAuthority("ROLE_" + claims.get("rol", String.class));
            var auth = new UsernamePasswordAuthenticationToken(claims, null, List.of(autoridad));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (Exception e) {
            response.setStatus(401);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"token invalido o vencido\"}");
        }
    }
}
