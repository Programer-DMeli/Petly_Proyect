package com.petly.kyc;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.jsonwebtoken.Claims;

@RestController
@RequestMapping("/api/kyc/verificaciones")
public class VerificacionKycController {

    private final VerificacionKycService service;

    public VerificacionKycController(VerificacionKycService service) {
        this.service = service;
    }

    @PostMapping("/{id}/consultar")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable long id, Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        long adoptanteId = Long.parseLong(claims.getSubject());
        return ResponseEntity.ok(service.verificar(id, adoptanteId));
    }
}
