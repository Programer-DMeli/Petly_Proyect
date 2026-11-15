package com.petly.resenas;
import jakarta.validation.constraints.NotNull;

public record ModeracionResenaRequest(
        @NotNull(message = "Indica si la reseña debe permanecer visible.")
        Boolean visible) {
}
