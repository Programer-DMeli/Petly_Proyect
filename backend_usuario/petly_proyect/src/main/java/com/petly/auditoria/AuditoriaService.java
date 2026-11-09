package com.petly.auditoria;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

@Service
public class AuditoriaService {

    private final EventoAuditoriaRepository eventos;

    public AuditoriaService(EventoAuditoriaRepository eventos) {
        this.eventos = eventos;
    }

    public void registrar(Long actorId, String actorRol, String accion, String recursoTipo, String recursoId) {
        eventos.save(new EventoAuditoria(
                null,
                actorId,
                actorRol,
                accion,
                "SPRING",
                recursoTipo,
                recursoId,
                LocalDateTime.now()));
    }
}
