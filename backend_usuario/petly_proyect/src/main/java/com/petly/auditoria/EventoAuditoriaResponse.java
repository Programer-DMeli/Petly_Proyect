package com.petly.auditoria;

import java.time.LocalDateTime;

/** Proyección de solo lectura para el visor administrativo. */
public record EventoAuditoriaResponse(
        String eventoId,
        Long actorUsuarioId,
        String actorRol,
        String accion,
        String origen,
        String recursoTipo,
        String recursoId,
        LocalDateTime ocurridoEn) {

    public static EventoAuditoriaResponse desde(EventoAuditoria evento) {
        return new EventoAuditoriaResponse(
                evento.getEventoId(),
                evento.getActorUsuarioId(),
                evento.getActorRol(),
                evento.getAccion(),
                evento.getOrigen(),
                evento.getRecursoTipo(),
                evento.getRecursoId(),
                evento.getOcurridoEn());
    }
}
