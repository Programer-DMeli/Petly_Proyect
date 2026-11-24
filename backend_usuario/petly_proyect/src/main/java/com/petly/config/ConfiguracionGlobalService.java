package com.petly.config;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.petly.auditoria.AuditoriaService;
import com.petly.common.exception.ApiException;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;

/** Lectura y validación de parámetros globales; la API ADMIN se agrega en US-75/2. */
@Service
public class ConfiguracionGlobalService {
    public static final int MAX_IMAGEN_MB_MINIMO = 1;
    public static final int MAX_IMAGEN_MB_MAXIMO = 20;
    public static final int EXPIRACION_SESION_DIAS_MINIMA = 1;
    public static final int EXPIRACION_SESION_DIAS_MAXIMA = 365;

    private final ConfiguracionGlobalRepository configuraciones;
    private final UsuarioRepository usuarios;
    private final AuditoriaService auditoria;

    public ConfiguracionGlobalService(
            ConfiguracionGlobalRepository configuraciones,
            UsuarioRepository usuarios,
            AuditoriaService auditoria) {
        this.configuraciones = configuraciones;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public ConfiguracionGlobal obtener() {
        return configuraciones.findById(ConfiguracionGlobal.CLAVE_GLOBAL)
                .orElseThrow(() -> new ApiException(503, "La configuración global no está inicializada."));
    }

    @Transactional
    public ConfiguracionGlobal actualizar(long actorId, int maxImagenMb, int expiracionSesionDias) {
        validar(maxImagenMb, expiracionSesionDias);
        Usuario actor = usuarios.findById(actorId)
                .orElseThrow(() -> new ApiException(401, "La sesión no identifica una cuenta válida."));
        if (actor.getRol() != Usuario.Rol.ADMIN) {
            throw new ApiException(403, "Solo ADMIN puede cambiar los parámetros globales.");
        }
        ConfiguracionGlobal configuracion = obtener();
        configuracion.setMaxImagenMb(maxImagenMb);
        configuracion.setExpiracionSesionDias(expiracionSesionDias);
        ConfiguracionGlobal guardada = configuraciones.saveAndFlush(configuracion);
        auditoria.registrar(actorId, actor.getRol().name(), "PARAMETROS_GLOBALES_ACTUALIZADOS",
                "CONFIGURACION_GLOBAL", ConfiguracionGlobal.CLAVE_GLOBAL);
        return guardada;
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
