package com.petly.security;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Cuenta los intentos de login por IP (US-21).
 *
 * Si una misma IP hace más de 10 pedidos a /api/auth en un minuto, responde
 * 429 y ya. Sirve para que nadie fuerce el login desde una sola máquina.
 * El bloqueo de cuenta (2 fallos / 5 min) va aparte, en AuthService.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMITE = 10;
    private static final long VENTANA_MS = 60_000;
    private static final String RUTA_AUTH = "/api/auth/";

    // ip: hora en que llegó cada pedido de esta ventana
    private final Map<String, Deque<Instant>> pedidos = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (!request.getRequestURI().startsWith(RUTA_AUTH)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = request.getRemoteAddr();
        Instant ahora = Instant.now();
        Deque<Instant> tiempos = pedidos.computeIfAbsent(ip, k -> new ArrayDeque<>());

        synchronized (tiempos) {
            // sacamos los que ya pasaron del minuto
            while (!tiempos.isEmpty() && tiempos.peekFirst().isBefore(ahora.minusSeconds(60))) {
                tiempos.pollFirst();
            }
            if (tiempos.size() >= LIMITE) {
                response.setStatus(429);
                response.getWriter().write("demasiados intentos, espera un minuto");
                return;
            }
            tiempos.addLast(ahora);
        }

        chain.doFilter(request, response);
    }
}
