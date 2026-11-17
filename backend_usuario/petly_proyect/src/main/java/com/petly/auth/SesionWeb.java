package com.petly.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import com.petly.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class SesionWeb {
    public static final String COOKIE = "petly_refresh";
    public static final String CANAL = "X-Petly-Client";

    private final List<String> origenes;
    private final boolean secure;
    private final String sameSite;

    public SesionWeb(@Value("${petly.session.web-origins:http://localhost:5173,http://127.0.0.1:5173}") String origenes,
            @Value("${petly.session.cookie-secure:false}") boolean secure,
            @Value("${petly.session.cookie-same-site:Lax}") String sameSite) {
        this.origenes = Arrays.stream(origenes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
        if (this.origenes.isEmpty() || this.origenes.stream().anyMatch(o -> !o.matches("https?://[^/*\\s]+"))) {
            throw new IllegalArgumentException("Configura orígenes web exactos para Petly");
        }
        if (!List.of("Lax", "Strict", "None").contains(sameSite) || ("None".equals(sameSite) && !secure)) {
            throw new IllegalArgumentException("Configuración SameSite inválida para Petly");
        }
        if (!secure && this.origenes.stream().anyMatch(origen -> origen.startsWith("https://"))) {
            throw new IllegalArgumentException("La cookie de sesión debe ser Secure con orígenes HTTPS");
        }
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public List<String> origenes() {
        return origenes;
    }

    boolean validarCanal(HttpServletRequest request) {
        String origen = request.getHeader("Origin");
        if ("web".equals(request.getHeader(CANAL)) && origenes.contains(origen == null ? "" : origen)) return true;
        if (origen != null || request.getHeader(CANAL) != null || token(request) != null) {
            throw new ApiException(403, "Origen de sesión no autorizado.");
        }
        return false;
    }

    String token(HttpServletRequest request) {
        var cookies = request.getCookies();
        if (cookies == null) return null;
        return Arrays.stream(cookies).filter(c -> COOKIE.equals(c.getName())).map(c -> c.getValue())
                .findFirst().orElse(null);
    }

    ResponseCookie cookie(String token, Instant expiraEn) {
        long segundos = Math.max(0, Duration.between(Instant.now(), expiraEn).toSeconds());
        return ResponseCookie.from(COOKIE, token).httpOnly(true).secure(secure)
                .sameSite(sameSite).path("/api/auth").maxAge(segundos).build();
    }
}
