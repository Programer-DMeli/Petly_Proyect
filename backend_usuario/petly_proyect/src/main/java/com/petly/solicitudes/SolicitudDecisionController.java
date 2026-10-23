package com.petly.solicitudes;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petly.solicitudes.dto.DecisionSolicitudRequest;

import jakarta.validation.Valid;

/** Coordina las decisiones del albergue con Django (US-07). */
@RestController
@RequestMapping("/api/albergue/solicitudes")
public class SolicitudDecisionController {

    private final SolicitudDecisionService solicitudDecisionService;

    public SolicitudDecisionController(SolicitudDecisionService solicitudDecisionService) {
        this.solicitudDecisionService = solicitudDecisionService;
    }

    @PostMapping("/{solicitudId}/decision")
    public ResponseEntity<String> decidir(
            @PathVariable Long solicitudId,
            @Valid @RequestBody DecisionSolicitudRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return solicitudDecisionService.decidir(solicitudId, request, authorization);
    }
}
