package com.petly.resenas;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ResenaItem(
        Long id,
        Integer calificacion,
        String comentario,
        @JsonProperty("creado_en") LocalDateTime creadoEn) {
}
