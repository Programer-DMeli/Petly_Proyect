package com.petly.solicitudes;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.petly.solicitudes.dto.CrearSolicitudAdopcionRequest;

import jakarta.validation.Valid;

/** API Spring que recibe la solicitud del adoptante y la coordina con Django (US-06). */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudAdopcionController {

    private final SolicitudAdopcionService solicitudService;

    public SolicitudAdopcionController(SolicitudAdopcionService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @PostMapping
    public ResponseEntity<String> crear(
            @Valid @RequestBody CrearSolicitudAdopcionRequest request,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return solicitudService.crear(request, authorization);
    }

    @GetMapping("/{solicitudId}/seguimiento")
    public ResponseEntity<String> seguimiento(
            @PathVariable Long solicitudId,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return solicitudService.obtenerSeguimiento(solicitudId, authorization);
    }
}
