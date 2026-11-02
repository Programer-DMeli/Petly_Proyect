package com.petly.notificaciones;

/**
 * Canales habilitados para las alertas de un usuario (US-45).
 * Los nulos permiten detectar campos omitidos en el PUT.
 */
public record PreferenciaNotificacion(Boolean email, Boolean whatsapp, Boolean push) {
    public static PreferenciaNotificacion porDefecto() {
        return new PreferenciaNotificacion(true, true, true);
    }

    public boolean completa() {
        return email != null && whatsapp != null && push != null;
    }
}
