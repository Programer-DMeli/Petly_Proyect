package com.petly.autorizacion;

import com.petly.autorizacion.dto.AutorizacionInternaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interno/autorizaciones")
public class AutorizacionInternoController {
    private final AutorizacionService autorizacionService;

    public AutorizacionInternoController(AutorizacionService autorizacionService) {
        this.autorizacionService = autorizacionService;
    }

    @GetMapping
    public AutorizacionInternaResponse vigencia(
        @RequestParam Long adoptanteId,
        @RequestParam Long albergueId) {
        return new AutorizacionInternaResponse(
            autorizacionService.estaAutorizado(adoptanteId, albergueId));
    }
}
