package com.petly.notificaciones;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Seguimiento de un aviso de coincidencia recibido desde Django y entregado por email. */
@Entity
@Table(name = "entregas_aviso_coincidencia",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_entrega_aviso_evento", columnNames = "evento_django_id"),
        indexes = @Index(name = "idx_entrega_aviso_estado_id", columnList = "estado, id"))
public class EntregaAvisoCoincidencia {

    public enum Estado {
        PENDIENTE,
        ENVIADO,
        OMITIDO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "evento_django_id", nullable = false)
    private Long eventoDjangoId;

    /** El ID vive en Spring; no hay FK entre las bases de Django y Spring. */
    @Column(name = "adoptante_id", nullable = false)
    private Long adoptanteId;

    @Column(name = "mascota_id", nullable = false)
    private Long mascotaId;

    @Column(name = "mascota_nombre", nullable = false, length = 150)
    private String mascotaNombre;

    @Column(name = "albergue_nombre", nullable = false, length = 150)
    private String albergueNombre;

    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private Estado estado = Estado.PENDIENTE;

    @Column(name = "enviado_en")
    private LocalDateTime enviadoEn;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected EntregaAvisoCoincidencia() {
    }

    public EntregaAvisoCoincidencia(
            Long eventoDjangoId,
            Long adoptanteId,
            Long mascotaId,
            String mascotaNombre,
            String albergueNombre,
            LocalDateTime ocurridoEn) {
        this.eventoDjangoId = eventoDjangoId;
        this.adoptanteId = adoptanteId;
        this.mascotaId = mascotaId;
        this.mascotaNombre = mascotaNombre;
        this.albergueNombre = albergueNombre;
        this.ocurridoEn = ocurridoEn;
        this.estado = Estado.PENDIENTE;
    }

    public Long getId() { return id; }
    public Long getEventoDjangoId() { return eventoDjangoId; }
    public Long getAdoptanteId() { return adoptanteId; }
    public Long getMascotaId() { return mascotaId; }
    public String getMascotaNombre() { return mascotaNombre; }
    public String getAlbergueNombre() { return albergueNombre; }
    public LocalDateTime getOcurridoEn() { return ocurridoEn; }
    public Estado getEstado() { return estado; }
    public LocalDateTime getEnviadoEn() { return enviadoEn; }

    public void marcarEnviado(LocalDateTime fecha) {
        this.estado = Estado.ENVIADO;
        this.enviadoEn = fecha;
    }

    public void omitir() {
        this.estado = Estado.OMITIDO;
    }
}
