package com.petly.solicitudes.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DecisionSolicitudRequest(
        @NotBlank
        @Pattern(regexp = "APROBADA|OBSERVADA|RECHAZADA")
        String estado,
        @Size(max = 2000)
        String observacionesRevision) {
}
