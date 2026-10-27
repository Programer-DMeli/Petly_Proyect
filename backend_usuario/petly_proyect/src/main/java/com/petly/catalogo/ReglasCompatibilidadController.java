package com.petly.catalogo;

import com.petly.common.exception.ApiException;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración del cálculo de compatibilidad; /api/admin/** exige rol ADMIN. */
@RestController
@RequestMapping("/api/admin/compatibilidad/reglas")
public class ReglasCompatibilidadController {
    private final ReglasCompatibilidadService reglas;

    public ReglasCompatibilidadController(ReglasCompatibilidadService reglas) {
        this.reglas = reglas;
    }

    @GetMapping
    public Map<String, Object> obtener() {
        return reglas.obtener().respuesta();
    }

    @PutMapping
    public Map<String, Object> actualizar(@RequestBody PesosRequest request, Authentication authentication) {
        if (request == null || request.pesos() == null
                || request.pesos().vivienda() == null
                || request.pesos().rutina() == null
                || request.pesos().convivencia() == null) {
            throw new ApiException(400, "Envía los tres pesos de compatibilidad.");
        }
        Claims claims = (Claims) authentication.getPrincipal();
        ReglasCompatibilidadService.Reglas actualizadas = reglas.actualizar(
                entero(request.pesos().vivienda()), entero(request.pesos().rutina()),
                entero(request.pesos().convivencia()),
                Long.parseLong(claims.getSubject()));
        return actualizadas.respuesta();
    }

    private int entero(java.math.BigDecimal value) {
        try { return value.intValueExact(); }
        catch (ArithmeticException ex) { throw new ApiException(400, "Los pesos deben ser enteros entre 0 y 100."); }
    }

    public record PesosRequest(Pesos pesos) {}
    public record Pesos(java.math.BigDecimal vivienda, java.math.BigDecimal rutina, java.math.BigDecimal convivencia) {}
}
