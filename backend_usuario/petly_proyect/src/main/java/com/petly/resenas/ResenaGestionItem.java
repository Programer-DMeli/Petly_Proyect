package com.petly.resenas;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ResenaGestionItem(
        Long id,
        Integer calificacion,
        String comentario,
        @JsonProperty("respuesta_albergue") String respuestaAlbergue,
        @JsonProperty("estado_moderacion") String estadoModeracion,
        @JsonProperty("creado_en") LocalDateTime creadoEn,
        @JsonProperty("moderada_en") LocalDateTime moderadaEn) {
}
