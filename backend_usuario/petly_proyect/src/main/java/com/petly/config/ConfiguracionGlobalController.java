package com.petly.config;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/configuracion-global")
public class ConfiguracionGlobalController {
    private final ConfiguracionGlobalService configuracion;

    public ConfiguracionGlobalController(ConfiguracionGlobalService configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping
    public ConfiguracionGlobalResponse obtener() {
        return ConfiguracionGlobalResponse.desde(configuracion.obtener());
    }

    @PutMapping
    public ConfiguracionGlobalResponse actualizar(
            @Valid @RequestBody ActualizarConfiguracionGlobalRequest request,
            Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        long actorId = Long.parseLong(claims.getSubject());
        ConfiguracionGlobal actualizada = configuracion.actualizar(
                actorId, request.maxImagenMb(), request.expiracionSesionDias());
        return ConfiguracionGlobalResponse.desde(actualizada);
    }
}
