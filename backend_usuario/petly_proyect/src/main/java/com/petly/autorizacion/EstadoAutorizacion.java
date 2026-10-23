package com.petly.autorizacion;

/**
 * Estado de una autorizacion de consulta (US-18).
 * ACTIVA: el adoptante autoriza compartir su perfil con un albergue.
 * REVOCADA: el adoptante retiro la autorizacion.
 */
public enum EstadoAutorizacion {
    ACTIVA,
    REVOCADA
}
