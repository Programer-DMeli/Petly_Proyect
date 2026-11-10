package com.petly.solicitudes;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> enviarReporte(
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @RequestParam("seguimiento_id") String seguimientoId,
            @RequestParam("estado_bienestar") String estadoBienestar,
            @RequestParam(value = "observaciones", required = false) String observaciones,
            @RequestParam(value = "fotos", required = false) List<MultipartFile> fotos) {
        return seguimientosService.enviarReporte(
                authorization, seguimientoId, estadoBienestar, observaciones, fotos);
    }
}
