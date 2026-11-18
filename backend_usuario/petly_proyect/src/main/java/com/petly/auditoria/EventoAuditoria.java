package com.petly.auditoria;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Evento append-only de auditoría del dominio Spring.
 * El identificador del actor es una instantánea y no una FK para conservar la
 * trazabilidad aunque la cuenta se elimine posteriormente.
 */
@Entity
@Table(
        name = "auditoria_evento",
        uniqueConstraints = @UniqueConstraint(name = "uq_auditoria_evento_uuid", columnNames = "evento_id"),
        indexes = {
                @Index(name = "idx_auditoria_actor_fecha", columnList = "actor_usuario_id, ocurrido_en, id"),
                @Index(name = "idx_auditoria_accion_fecha", columnList = "accion, ocurrido_en, id"),
                @Index(name = "idx_auditoria_fecha", columnList = "ocurrido_en, id")
        })
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_id", nullable = false, length = 36, updatable = false)
    private String eventoId;

    /** ID de la cuenta Petly (Spring), conservado como valor histórico sin FK. */
    @Column(name = "actor_usuario_id")
    private Long actorUsuarioId;

    @Column(name = "actor_rol", nullable = false, length = 20, updatable = false)
    private String actorRol;

    @Column(name = "accion", nullable = false, length = 80, updatable = false)
    private String accion;

    @Column(name = "origen", nullable = false, length = 10, updatable = false)
    private String origen;

    @Column(name = "recurso_tipo", length = 80, updatable = false)
    private String recursoTipo;

    @Column(name = "recurso_id", length = 100, updatable = false)
    private String recursoId;

    @Column(name = "ocurrido_en", nullable = false, updatable = false)
    private LocalDateTime ocurridoEn;

    protected EventoAuditoria() {
    }

    public EventoAuditoria(
            String eventoId,
            Long actorUsuarioId,
            String actorRol,
            String accion,
            String origen,
            String recursoTipo,
            String recursoId,
            LocalDateTime ocurridoEn) {
        this.eventoId = eventoId == null ? UUID.randomUUID().toString() : eventoId;
        this.actorUsuarioId = actorUsuarioId;
        this.actorRol = actorRol;
        this.accion = accion;
        this.origen = origen;
        this.recursoTipo = recursoTipo;
        this.recursoId = recursoId;
        this.ocurridoEn = ocurridoEn == null ? LocalDateTime.now() : ocurridoEn;
    }

    public Long getId() { return id; }
    public String getEventoId() { return eventoId; }
    public Long getActorUsuarioId() { return actorUsuarioId; }
    public String getActorRol() { return actorRol; }
    public String getAccion() { return accion; }
    public String getOrigen() { return origen; }
    public String getRecursoTipo() { return recursoTipo; }
    public String getRecursoId() { return recursoId; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
}
