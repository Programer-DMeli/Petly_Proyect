package com.petly.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

public class InternalApiFilter extends OncePerRequestFilter {
    private final String secretoEsperado;
    public InternalApiFilter(String secretoEsperado) { this.secretoEsperado = secretoEsperado; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        if (req.getRequestURI().startsWith("/api/interno/")) {
            String recibido = req.getHeader("X-Internal-Secret");
            if (!secretoEsperado.equals(recibido)) {
                res.setStatus(401);
                res.setContentType("application/json");
                res.getWriter().write("{\"error\":\"no autorizado\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
