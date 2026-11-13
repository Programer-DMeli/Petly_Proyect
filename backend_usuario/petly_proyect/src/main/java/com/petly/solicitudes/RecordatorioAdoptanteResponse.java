package com.petly.solicitudes;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Datos públicos mínimos del recordatorio para el adoptante propietario. */
public record RecordatorioAdoptanteResponse(
        Long id,
        @JsonProperty("seguimiento_id") Long seguimientoId,
        @JsonProperty("fecha_limite") LocalDateTime fechaLimite,
        @JsonProperty("anticipacion_dias") int anticipacionDias,
        String estado,
        @JsonProperty("enviado_en") LocalDateTime enviadoEn) {

    static RecordatorioAdoptanteResponse desde(RecordatorioSeguimiento recordatorio) {
        return new RecordatorioAdoptanteResponse(
                recordatorio.getId(),
                recordatorio.getSeguimientoId(),
                recordatorio.getFechaLimite(),
                recordatorio.getAnticipacionDias(),
                recordatorio.getEstado().name(),
                recordatorio.getEnviadoEn());
    }
}
