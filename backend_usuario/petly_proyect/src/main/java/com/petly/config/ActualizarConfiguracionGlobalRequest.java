package com.petly.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActualizarConfiguracionGlobalRequest(
        @NotNull @Min(1) @Max(20) Integer maxImagenMb,
        @NotNull @Min(1) @Max(365) Integer expiracionSesionDias) {}
