package com.petly.comunidad;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ComentarioCasoExitoResponse(
        Long id,
        String autor,
        String comentario,
        @JsonProperty("creado_en") LocalDateTime creadoEn) {
}
