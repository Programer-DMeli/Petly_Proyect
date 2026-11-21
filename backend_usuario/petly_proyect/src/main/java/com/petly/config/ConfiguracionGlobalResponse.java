package com.petly.config;

import java.time.LocalDateTime;

public record ConfiguracionGlobalResponse(
        int maxImagenMb,
        int expiracionSesionDias,
        LocalDateTime actualizadoEn) {

    public static ConfiguracionGlobalResponse desde(ConfiguracionGlobal configuracion) {
        return new ConfiguracionGlobalResponse(
                configuracion.getMaxImagenMb(),
                configuracion.getExpiracionSesionDias(),
                configuracion.getActualizadoEn());
    }
}
