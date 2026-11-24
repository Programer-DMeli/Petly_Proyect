package com.petly.config;

import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
public class ConfiguracionGlobalController {
    private static final String RUTA_ADMIN = "/api/admin/configuracion-global";
    private final ConfiguracionGlobalService configuracion;

    public ConfiguracionGlobalController(ConfiguracionGlobalService configuracion) {
        this.configuracion = configuracion;
    }

    @GetMapping(RUTA_ADMIN)
    public ConfiguracionGlobalResponse obtener() {
        return ConfiguracionGlobalResponse.desde(configuracion.obtener());
    }

    @GetMapping("/api/configuracion/limite-imagen")
    public ResponseEntity<Map<String, Integer>> obtenerLimiteImagen() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("maxImagenMb", configuracion.obtener().getMaxImagenMb()));
    }

    @PutMapping(RUTA_ADMIN)
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
