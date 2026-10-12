package com.petly.autorizacion;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.petly.autorizacion.dto.AutorizacionResponse;

/**
 * Logica de negocio de las autorizaciones de consulta (US-18).
 * Autorizar es idempotente: si ya existe se reactiva en lugar de duplicar.
 */
@Service
public class AutorizacionService {

    private final AutorizacionRepository autorizaciones;

    public AutorizacionService(AutorizacionRepository autorizaciones) {
        this.autorizaciones = autorizaciones;
    }

    @Transactional
    public AutorizacionResponse autorizar(Long adoptanteId, Long albergueId) {
        validarAlbergue(albergueId);
        Autorizacion autorizacion = autorizaciones
                .findByAdoptanteIdAndAlbergueId(adoptanteId, albergueId)
                .orElseGet(() -> new Autorizacion(adoptanteId, albergueId));
        if (!autorizacion.isActiva()) {
            autorizacion.activar();
        }
        return AutorizacionResponse.from(autorizaciones.save(autorizacion));
    }

    @Transactional(readOnly = true)
    public List<AutorizacionResponse> listar(Long adoptanteId) {
        return autorizaciones.findByAdoptanteIdOrderByAlbergueIdAsc(adoptanteId)
                .stream()
                .map(AutorizacionResponse::from)
                .toList();
    }

    @Transactional
    public void revocar(Long adoptanteId, Long albergueId) {
        Autorizacion autorizacion = autorizaciones
                .findByAdoptanteIdAndAlbergueId(adoptanteId, albergueId)
                .orElseThrow(() -> new AutorizacionNoEncontradaException(
                        "No existe una autorizacion para ese albergue"));
        if (autorizacion.isActiva()) {
            autorizacion.revocar();
            autorizaciones.save(autorizacion);
        }
    }

    /** Vigencia de la autorizacion para el endpoint interno de Django (US-18). */
    @Transactional(readOnly = true)
    public boolean estaAutorizado(Long adoptanteId, Long albergueId) {
        if (adoptanteId == null || albergueId == null) {
            throw new IllegalArgumentException("adoptanteId y albergueId son obligatorios");
        }
        return autorizaciones.existsByAdoptanteIdAndAlbergueIdAndEstado(
                adoptanteId, albergueId, EstadoAutorizacion.ACTIVA);
    }

    private void validarAlbergue(Long albergueId) {
        if (albergueId == null || albergueId <= 0) {
            throw new IllegalArgumentException("albergueId invalido");
        }
    }
}
