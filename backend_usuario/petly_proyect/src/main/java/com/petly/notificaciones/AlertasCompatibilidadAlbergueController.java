package com.petly.notificaciones;

import java.util.Map;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja privada de alertas para el albergue autenticado (US-42). */
@RestController
@RequestMapping("/api/albergue/alertas-compatibilidad")
public class AlertasCompatibilidadAlbergueController {
    private final AlertaCompatibilidadService service;

    public AlertasCompatibilidadAlbergueController(AlertaCompatibilidadService service) {
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> listar(Authentication authentication) {
        return service.listarParaAlbergue(albergueId(authentication));
    }

    @PostMapping("/{id}/leer")
    public AlertaCompatibilidadResponse marcarLeida(Authentication authentication, @PathVariable long id) {
        return service.marcarLeida(albergueId(authentication), id);
    }

    private static long albergueId(Authentication authentication) {
        return Long.parseLong(((Claims) authentication.getPrincipal()).getSubject());
    }
}
