package com.petly.notificaciones;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.petly.common.exception.ApiException;

/**
 * Preferencias de canal de notificación por usuario (US-45).
 * Sin fila guardada, todos los canales quedan habilitados.
 */
@Service
public class PreferenciasNotificacionService {
    private final JdbcTemplate db;

    public PreferenciasNotificacionService(JdbcTemplate db) {
        this.db = db;
    }

    public PreferenciaNotificacion obtener(long usuarioId) {
        List<PreferenciaNotificacion> filas = db.query(
                "SELECT email, whatsapp, push FROM preferencias_notificacion WHERE usuario_id=?",
                (rs, n) -> new PreferenciaNotificacion(
                        rs.getBoolean("email"),
                        rs.getBoolean("whatsapp"),
                        rs.getBoolean("push")),
                usuarioId);
        return filas.isEmpty() ? PreferenciaNotificacion.porDefecto() : filas.get(0);
    }

    @Transactional
    public PreferenciaNotificacion guardar(long usuarioId, PreferenciaNotificacion preferencia) {
        if (preferencia == null || !preferencia.completa())
            throw new ApiException(400, "Debes indicar email, whatsapp y push.");
        db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE", Long.class, usuarioId);
        int actualizadas = db.update(
                "UPDATE preferencias_notificacion SET email=?, whatsapp=?, push=?, actualizado_en=CURRENT_TIMESTAMP WHERE usuario_id=?",
                preferencia.email(), preferencia.whatsapp(), preferencia.push(), usuarioId);
        if (actualizadas == 0)
            db.update("INSERT INTO preferencias_notificacion(usuario_id, email, whatsapp, push) VALUES (?,?,?,?)",
                    usuarioId, preferencia.email(), preferencia.whatsapp(), preferencia.push());
        return preferencia;
    }
}
