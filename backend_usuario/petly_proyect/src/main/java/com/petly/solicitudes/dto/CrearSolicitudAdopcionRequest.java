package com.petly.solicitudes.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Datos que el adoptante puede enviar para crear una solicitud (US-06). */
public record CrearSolicitudAdopcionRequest(
        @NotNull(message = "mascotaId es obligatorio")
        @Positive(message = "mascotaId debe ser positivo")
        Long mascotaId,
        @Size(max = 2000, message = "mensajePostulante no puede superar 2000 caracteres")
        String mensajePostulante,
        Map<String, Object> respuestasCuestionario) {
}
