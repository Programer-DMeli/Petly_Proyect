package com.petly.notificaciones;

import java.time.LocalDateTime;

public record AlertaCompatibilidadResponse(
        Long id,
        Long solicitudId,
        Long adoptanteId,
        Long mascotaId,
        String adoptanteNombre,
        String mascotaNombre,
        Integer porcentaje,
        LocalDateTime ocurridoEn,
        LocalDateTime leidoEn) {
    static AlertaCompatibilidadResponse desde(AlertaCompatibilidadAlbergue alerta) {
        return new AlertaCompatibilidadResponse(alerta.getId(), alerta.getSolicitudId(),
                alerta.getAdoptanteId(), alerta.getMascotaId(), alerta.getAdoptanteNombre(),
                alerta.getMascotaNombre(), alerta.getPorcentaje(), alerta.getOcurridoEn(), alerta.getLeidoEn());
    }
}
