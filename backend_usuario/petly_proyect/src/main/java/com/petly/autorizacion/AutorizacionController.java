package com.petly.autorizacion;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petly.autorizacion.dto.AutorizacionRequest;
import com.petly.autorizacion.dto.AutorizacionResponse;

import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * API de autorizacion de consulta del adoptante (US-18).
 * El adoptante autoriza, lista y revoca el acceso de un albergue a su perfil.
 */
@RestController
@RequestMapping("/api/autorizaciones")
@Tag(name = "Autorizaciones", description = "Permisos de consulta del perfil del adoptante")
public class AutorizacionController {

    private final AutorizacionService autorizacionService;

    public AutorizacionController(AutorizacionService autorizacionService) {
        this.autorizacionService = autorizacionService;
    }

    @PostMapping
    @Operation(summary = "Autoriza a un albergue a consultar el perfil")
    public ResponseEntity<AutorizacionResponse> autorizar(@Valid @RequestBody AutorizacionRequest request) {
        AutorizacionResponse respuesta =
                autorizacionService.autorizar(adoptanteActual(), request.getAlbergueId());
        return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
    }

    @GetMapping
    @Operation(summary = "Lista las autorizaciones del adoptante")
    public List<AutorizacionResponse> listar() {
        return autorizacionService.listar(adoptanteActual());
    }

    @DeleteMapping("/{albergueId}")
    @Operation(summary = "Revoca la autorizacion hacia un albergue")
    public ResponseEntity<Map<String, String>> revocar(@PathVariable Long albergueId) {
        autorizacionService.revocar(adoptanteActual(), albergueId);
        return ResponseEntity.ok(Map.of("detail", "Autorizacion revocada."));
    }

    private Long adoptanteActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Claims claims = (Claims) auth.getPrincipal();
        return Long.valueOf(claims.getSubject());
    }
}
