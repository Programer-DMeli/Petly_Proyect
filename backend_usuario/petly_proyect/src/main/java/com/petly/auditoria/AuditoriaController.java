package com.petly.auditoria;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de auditoría de solo lectura; la ruta /api/admin/** exige ADMIN. */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {

    private final AuditoriaConsultaService consulta;

    public AuditoriaController(AuditoriaConsultaService consulta) {
        this.consulta = consulta;
    }

    @GetMapping
    public Page<EventoAuditoriaResponse> buscar(
            @RequestParam(required = false) Long actorId,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return consulta.buscar(actorId, accion, desde, hasta, pagina, tamano);
    }
}
