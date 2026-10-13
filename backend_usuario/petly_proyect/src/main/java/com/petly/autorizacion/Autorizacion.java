package com.petly.autorizacion;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Autorizacion de consulta del perfil (US-18, Persona B).
 * Tabla creada por Flyway V4; Hibernate no la crea (ddl-auto: none).
 * Relaciona un adoptante (usuarios.id) con un albergue especifico y su estado.
 */
@Entity
@Table(name = "autorizaciones_consulta",
        uniqueConstraints = @UniqueConstraint(columnNames = {"adoptante_id", "albergue_id"}))
public class Autorizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "adoptante_id", nullable = false)
    private Long adoptanteId;

    @Column(name = "albergue_id", nullable = false)
    private Long albergueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 10)
    private EstadoAutorizacion estado = EstadoAutorizacion.ACTIVA;

    @Column(name = "fecha_autorizacion", nullable = false)
    private LocalDateTime fechaAutorizacion;

    @Column(name = "fecha_revocacion")
    private LocalDateTime fechaRevocacion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Autorizacion() {
    }

    public Autorizacion(Long adoptanteId, Long albergueId) {
        this.adoptanteId = adoptanteId;
        this.albergueId = albergueId;
        this.estado = EstadoAutorizacion.ACTIVA;
        this.fechaAutorizacion = LocalDateTime.now();
    }

    /** Reactiva una autorizacion revocada (o deja intacta una activa). */
    public void activar() {
        this.estado = EstadoAutorizacion.ACTIVA;
        this.fechaAutorizacion = LocalDateTime.now();
        this.fechaRevocacion = null;
    }

    /** Revoca la autorizacion y registra la fecha. */
    public void revocar() {
        this.estado = EstadoAutorizacion.REVOCADA;
        this.fechaRevocacion = LocalDateTime.now();
    }

    public boolean isActiva() {
        return this.estado == EstadoAutorizacion.ACTIVA;
    }

    public Long getId() {
        return id;
    }

    public Long getAdoptanteId() {
        return adoptanteId;
    }

    public Long getAlbergueId() {
        return albergueId;
    }

    public EstadoAutorizacion getEstado() {
        return estado;
    }

    public LocalDateTime getFechaAutorizacion() {
        return fechaAutorizacion;
    }

    public LocalDateTime getFechaRevocacion() {
        return fechaRevocacion;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
