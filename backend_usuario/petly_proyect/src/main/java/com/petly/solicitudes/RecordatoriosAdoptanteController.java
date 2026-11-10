package com.petly.solicitudes;

import java.util.List;

import io.jsonwebtoken.Claims;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API autenticada para que cada adoptante consulte únicamente sus recordatorios. */
@RestController
@RequestMapping("/api/seguimientos/adoptante/recordatorios")
public class RecordatoriosAdoptanteController {

    private final RecordatoriosAdoptanteService service;

    public RecordatoriosAdoptanteController(RecordatoriosAdoptanteService service) {
        this.service = service;
    }

    @GetMapping
    public List<RecordatorioAdoptanteResponse> listar(Authentication authentication) {
        long adoptanteId = Long.parseLong(((Claims) authentication.getPrincipal()).getSubject());
        return service.listar(adoptanteId);
    }
}
