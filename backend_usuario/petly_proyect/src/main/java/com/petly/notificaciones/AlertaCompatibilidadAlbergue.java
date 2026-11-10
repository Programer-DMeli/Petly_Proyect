package com.petly.notificaciones;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Alerta persistente para el albergue cuando una solicitud supera 90 % (US-42). */
@Entity
@Table(name = "alertas_compatibilidad_albergue",
        uniqueConstraints = @UniqueConstraint(name = "uq_alerta_compat_solicitud", columnNames = "solicitud_id"),
        indexes = {
                @Index(name = "idx_alerta_compat_albergue_fecha", columnList = "albergue_usuario_id, ocurrido_en, id"),
                @Index(name = "idx_alerta_compat_albergue_leido", columnList = "albergue_usuario_id, leido_en")
        })
public class AlertaCompatibilidadAlbergue {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "solicitud_id", nullable = false)
    private Long solicitudId;

    /** ID de la cuenta ALBERGUE en Spring; no existe FK entre los servicios. */
    @Column(name = "albergue_usuario_id", nullable = false)
    private Long albergueUsuarioId;

    @Column(name = "adoptante_id", nullable = false)
    private Long adoptanteId;

    @Column(name = "mascota_id", nullable = false)
    private Long mascotaId;

    @Column(name = "adoptante_nombre", nullable = false, length = 150)
    private String adoptanteNombre;

    @Column(name = "mascota_nombre", nullable = false, length = 150)
    private String mascotaNombre;

    @Column(name = "porcentaje", nullable = false)
    private Integer porcentaje;

    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;

    @Column(name = "leido_en")
    private LocalDateTime leidoEn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AlertaCompatibilidadAlbergue() { }

    public AlertaCompatibilidadAlbergue(Long solicitudId, Long albergueUsuarioId, Long adoptanteId,
            Long mascotaId, String adoptanteNombre, String mascotaNombre, Integer porcentaje,
            LocalDateTime ocurridoEn) {
        this.solicitudId = solicitudId;
        this.albergueUsuarioId = albergueUsuarioId;
        this.adoptanteId = adoptanteId;
        this.mascotaId = mascotaId;
        this.adoptanteNombre = adoptanteNombre;
        this.mascotaNombre = mascotaNombre;
        this.porcentaje = porcentaje;
        this.ocurridoEn = ocurridoEn;
    }

    public Long getId() { return id; }
    public Long getSolicitudId() { return solicitudId; }
    public Long getAlbergueUsuarioId() { return albergueUsuarioId; }
    public Long getAdoptanteId() { return adoptanteId; }
    public Long getMascotaId() { return mascotaId; }
    public String getAdoptanteNombre() { return adoptanteNombre; }
    public String getMascotaNombre() { return mascotaNombre; }
    public Integer getPorcentaje() { return porcentaje; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
    public LocalDateTime getLeidoEn() { return leidoEn; }

    public void marcarLeida(LocalDateTime fecha) {
        if (leidoEn == null) leidoEn = fecha;
    }
}
