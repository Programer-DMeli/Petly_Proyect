package com.petly.comunidad;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ComentarioCasoExitoRequest(
        @NotBlank(message = "El comentario no puede estar vacío.")
        @Size(max = 1000, message = "El comentario no puede superar los 1000 caracteres.")
        String comentario) {
}
