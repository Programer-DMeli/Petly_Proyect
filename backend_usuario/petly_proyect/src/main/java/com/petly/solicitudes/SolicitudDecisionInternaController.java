package com.petly.solicitudes;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.petly.autorizacion.AutorizacionService;
import com.petly.common.exception.ApiException;
import com.petly.perfiles.PerfilService;
import com.petly.solicitudes.dto.ElegibilidadDecisionResponse;

/** Consulta los datos de Spring necesarios antes de aprobar una solicitud (US-07). */
@RestController
@RequestMapping("/api/interno/adopciones")
public class SolicitudDecisionInternaController {

    private final PerfilService perfilService;
    private final AutorizacionService autorizacionService;

    public SolicitudDecisionInternaController(
            PerfilService perfilService,
            AutorizacionService autorizacionService) {
        this.perfilService = perfilService;
        this.autorizacionService = autorizacionService;
    }

    @GetMapping("/elegibilidad")
    public ElegibilidadDecisionResponse elegibilidad(
            @RequestParam Long adoptanteId,
            @RequestParam Long albergueId) {
        if (adoptanteId <= 0 || albergueId <= 0) {
            throw new ApiException(400, "adoptanteId y albergueId deben ser positivos.");
        }

        Map<String, Object> perfil = perfilService.get(adoptanteId);
        return new ElegibilidadDecisionResponse(
                Boolean.TRUE.equals(perfil.get("finalizado")),
                autorizacionService.estaAutorizado(adoptanteId, albergueId));
    }
}
