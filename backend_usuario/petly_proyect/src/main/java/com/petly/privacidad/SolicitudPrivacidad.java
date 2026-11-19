package com.petly.privacidad;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "solicitudes_privacidad")
public class SolicitudPrivacidad {

    public enum Tipo { EXPORTACION, BAJA }
    public enum Estado { PENDIENTE_DJANGO, PENDIENTE_ARCHIVOS, COMPLETADA, ERROR }

    @Id
    @Column(name = "operacion_id", length = 36)
    private String operacionId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Estado estado;

    @Column(name = "ultimo_error", nullable = false, length = 500)
    private String ultimoError = "";

    @Column(name = "retenciones", nullable = false, columnDefinition = "TEXT")
    private String retenciones = "";

    @CreationTimestamp
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    @Column(name = "completado_en")
    private LocalDateTime completadoEn;

    protected SolicitudPrivacidad() {}

    public SolicitudPrivacidad(String operacionId, Long usuarioId, Tipo tipo) {
        this.operacionId = operacionId;
        this.usuarioId = usuarioId;
        this.tipo = tipo;
        this.estado = Estado.PENDIENTE_DJANGO;
    }

    public String getOperacionId() { return operacionId; }
    public Long getUsuarioId() { return usuarioId; }
    public Tipo getTipo() { return tipo; }
    public Estado getEstado() { return estado; }
    public String getUltimoError() { return ultimoError; }
    public String getRetenciones() { return retenciones; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
    public LocalDateTime getCompletadoEn() { return completadoEn; }

    public void marcarPendienteDjango(String error) {
        estado = Estado.PENDIENTE_DJANGO;
        ultimoError = error;
        completadoEn = null;
    }

    public void marcarPendienteArchivos() {
        estado = Estado.PENDIENTE_ARCHIVOS;
        ultimoError = "";
        completadoEn = null;
    }

    public void completar() {
        estado = Estado.COMPLETADA;
        ultimoError = "";
        completadoEn = LocalDateTime.now();
    }

    public void marcarError(String error) {
        estado = Estado.ERROR;
        ultimoError = error;
    }

    public void guardarRetenciones(List<String> valores) {
        retenciones = String.join("\n", valores);
    }
}
