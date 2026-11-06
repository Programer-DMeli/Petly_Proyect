package com.petly.solicitudes;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API Spring para consultar los seguimientos postadopción del adoptante (US-37). */
@RestController
@RequestMapping("/api/seguimientos/adoptante")
public class SeguimientosAdoptanteController {

    private final SeguimientosAdoptanteService seguimientosService;

    public SeguimientosAdoptanteController(SeguimientosAdoptanteService seguimientosService) {
        this.seguimientosService = seguimientosService;
    }

    @GetMapping
    public ResponseEntity<String> listarSeguimientos(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return seguimientosService.listarSeguimientos(authorization);
    }
}
