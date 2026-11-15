package com.petly.resenas;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearResenaRequest(
        @NotNull(message = "La calificación es obligatoria.")
        @Min(value = 1, message = "La calificación mínima es 1 estrella.")
        @Max(value = 5, message = "La calificación máxima es 5 estrellas.")
        Integer calificacion,
        @NotBlank(message = "Escribe un comentario sobre la atención recibida.")
        @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres.")
        String comentario) {
}
