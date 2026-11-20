package com.petly.config;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.petly.common.exception.ApiException;

/** Lectura y validación de parámetros globales; la API ADMIN se agrega en US-75/2. */
@Service
public class ConfiguracionGlobalService {
    public static final int MAX_IMAGEN_MB_MINIMO = 1;
    public static final int MAX_IMAGEN_MB_MAXIMO = 20;
    public static final int EXPIRACION_SESION_DIAS_MINIMA = 1;
    public static final int EXPIRACION_SESION_DIAS_MAXIMA = 365;

    private final ConfiguracionGlobalRepository configuraciones;

    public ConfiguracionGlobalService(ConfiguracionGlobalRepository configuraciones) {
        this.configuraciones = configuraciones;
    }

    @Transactional(readOnly = true)
    public ConfiguracionGlobal obtener() {
        return configuraciones.findById(ConfiguracionGlobal.CLAVE_GLOBAL)
                .orElseGet(() -> new ConfiguracionGlobal(
                        ConfiguracionGlobal.MAX_IMAGEN_MB_PREDETERMINADO,
                        ConfiguracionGlobal.EXPIRACION_SESION_DIAS_PREDETERMINADA));
    }

    @Transactional
    public ConfiguracionGlobal actualizar(int maxImagenMb, int expiracionSesionDias) {
        validar(maxImagenMb, expiracionSesionDias);
        ConfiguracionGlobal configuracion = configuraciones.findById(ConfiguracionGlobal.CLAVE_GLOBAL)
                .orElseGet(() -> new ConfiguracionGlobal(
                        ConfiguracionGlobal.MAX_IMAGEN_MB_PREDETERMINADO,
                        ConfiguracionGlobal.EXPIRACION_SESION_DIAS_PREDETERMINADA));
        configuracion.setMaxImagenMb(maxImagenMb);
        configuracion.setExpiracionSesionDias(expiracionSesionDias);
        return configuraciones.save(configuracion);
    }

    private static void validar(int maxImagenMb, int expiracionSesionDias) {
        if (maxImagenMb < MAX_IMAGEN_MB_MINIMO || maxImagenMb > MAX_IMAGEN_MB_MAXIMO) {
            throw new ApiException(400, "El límite de imágenes debe estar entre 1 y 20 MB.",
                    Map.of("maxImagenMb", "Usa un valor entre 1 y 20 MB."));
        }
        if (expiracionSesionDias < EXPIRACION_SESION_DIAS_MINIMA
                || expiracionSesionDias > EXPIRACION_SESION_DIAS_MAXIMA) {
            throw new ApiException(400, "La duración de sesión debe estar entre 1 y 365 días.",
                    Map.of("expiracionSesionDias", "Usa un valor entre 1 y 365 días."));
        }
    }
}
