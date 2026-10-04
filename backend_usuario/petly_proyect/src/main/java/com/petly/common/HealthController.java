package com.petly.common;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GET /api/health/ — comprobación básica de vida del servicio.
 *
 * Devuelve únicamente el estado: sin versión, sin detalle de base de datos,
 * sin rutas ni configuración (PLANNING v2 §10.5).
 */
@RestController
@RequestMapping({ "/api/health", "/api/health/" })
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
