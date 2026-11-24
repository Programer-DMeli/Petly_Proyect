package com.petly.privacidad;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/privacidad")
public class PrivacidadController {

    public record SolicitarExportacionRequest(@NotNull UUID operacionId) {}

    public record SolicitarBajaRequest(@NotNull UUID operacionId, @AssertTrue boolean confirmar) {}

    private final PrivacidadService privacidad;

    public PrivacidadController(PrivacidadService privacidad) {
        this.privacidad = privacidad;
    }

    @PostMapping("/exportacion")
    public ResponseEntity<Map<String, Object>> exportar(
            @Valid @RequestBody SolicitarExportacionRequest request,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(privacidad.exportar(usuarioId(authentication), request.operacionId()));
    }

    @PostMapping("/baja")
    public ResponseEntity<Map<String, Object>> solicitarBaja(
            @Valid @RequestBody SolicitarBajaRequest request,
            Authentication authentication) {
        Map<String, Object> resultado = privacidad.solicitarBaja(usuarioId(authentication), request.operacionId());
        HttpStatus status = resultado.get("estado") == SolicitudPrivacidad.Estado.COMPLETADA
                ? HttpStatus.OK : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(resultado);
    }

    @GetMapping("/operaciones/{operacionId}")
    public ResponseEntity<Map<String, Object>> estado(
            @PathVariable UUID operacionId,
            Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(privacidad.estado(usuarioId(authentication), operacionId));
    }

    private long usuarioId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Claims claims)) {
            throw new com.petly.common.exception.ApiException(401, "Inicia sesión para continuar.");
        }
        return Long.parseLong(claims.getSubject());
    }
}
