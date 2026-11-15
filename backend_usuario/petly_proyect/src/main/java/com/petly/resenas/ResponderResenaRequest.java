package com.petly.resenas;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponderResenaRequest(
        @NotBlank(message = "La respuesta no puede estar vacía.")
        @Size(max = 1000, message = "La respuesta no puede superar los 1000 caracteres.")
        String respuesta) {
}
