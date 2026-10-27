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
@RequestMapping("/api/kyc")
public class VerificacionKycController {

    private final VerificacionKycService service;
    private final ApiPeruDniClient apiPeruDni;

    public VerificacionKycController(VerificacionKycService service, ApiPeruDniClient apiPeruDni) {
        this.service = service;
        this.apiPeruDni = apiPeruDni;
    }

    @PostMapping("/dni")
    public ResponseEntity<ApiPeruDniResult> consultarDni(@org.springframework.web.bind.annotation.RequestBody ApiPeruDniRequest request) {
        return ResponseEntity.ok(apiPeruDni.consultar(request.dni()));
    }

    @PostMapping("/verificaciones/{id}/consultar")
    public ResponseEntity<Map<String, Object>> consultar(@PathVariable long id, Authentication authentication) {
        Claims claims = (Claims) authentication.getPrincipal();
        long adoptanteId = Long.parseLong(claims.getSubject());
        return ResponseEntity.ok(service.verificar(id, adoptanteId));
    }
}
