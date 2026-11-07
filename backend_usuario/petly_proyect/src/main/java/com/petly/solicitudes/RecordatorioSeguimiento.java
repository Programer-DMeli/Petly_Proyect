package com.petly.solicitudes;

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

/** Recordatorio de un reporte de bienestar asociado a un seguimiento (US-43). */
@Entity
@Table(name = "recordatorios_seguimiento",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_recordatorio_seguimiento", columnNames = "seguimiento_id"),
        indexes = @Index(
                name = "idx_recordatorio_adoptante_estado_fecha",
                columnList = "adoptante_id, estado, fecha_limite"))
public class RecordatorioSeguimiento {

    public enum Estado {
        PENDIENTE,
        ENVIADO,
        COMPLETADO,
        CANCELADO
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** ID del seguimiento administrado por Django. */
    @Column(name = "seguimiento_id", nullable = false)
    private Long seguimientoId;

    /** ID del adoptante en la base de cuentas de Spring Boot. */
    @Column(name = "adoptante_id", nullable = false)
    private Long adoptanteId;

    @Column(name = "fecha_limite", nullable = false)
    private LocalDateTime fechaLimite;

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

    protected RecordatorioSeguimiento() {
    }

    public RecordatorioSeguimiento(Long seguimientoId, Long adoptanteId, LocalDateTime fechaLimite) {
        this.seguimientoId = seguimientoId;
        this.adoptanteId = adoptanteId;
        this.fechaLimite = fechaLimite;
        this.estado = Estado.PENDIENTE;
    }

    public Long getId() {
        return id;
    }

    public Long getSeguimientoId() {
        return seguimientoId;
    }

    public Long getAdoptanteId() {
        return adoptanteId;
    }

    public LocalDateTime getFechaLimite() {
        return fechaLimite;
    }

    public Estado getEstado() {
        return estado;
    }

    public LocalDateTime getEnviadoEn() {
        return enviadoEn;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void actualizarFechaLimite(LocalDateTime fechaLimite) {
        this.fechaLimite = fechaLimite;
    }

    public void marcarEnviado(LocalDateTime enviadoEn) {
        this.estado = Estado.ENVIADO;
        this.enviadoEn = enviadoEn;
    }

    public void completar() {
        this.estado = Estado.COMPLETADO;
    }

    public void cancelar() {
        this.estado = Estado.CANCELADO;
    }
}
