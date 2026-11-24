package com.petly.resenas;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ResenaRepository {

    private final JdbcTemplate db;

    public ResenaRepository(JdbcTemplate db) {
        this.db = db;
    }

    public boolean existeAlbergue(long albergueId) {
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM albergues WHERE id = ?",
                Long.class,
                albergueId);
        return total != null && total > 0;
    }

    public List<ResenaItem> listar(long albergueId) {
        return db.query(
                "SELECT id, calificacion, comentario, respuesta_albergue, created_at FROM resenas_albergue "
                        + "WHERE albergue_id = ? AND estado_moderacion = 'VISIBLE' "
                        + "ORDER BY created_at DESC, id DESC",
                (rs, row) -> {
                    Timestamp creado = rs.getTimestamp("created_at");
                    return new ResenaItem(
                            rs.getLong("id"),
                            rs.getInt("calificacion"),
                            rs.getString("comentario"),
                            rs.getString("respuesta_albergue"),
                            creado == null ? null : creado.toLocalDateTime());
                },
                albergueId);
    }

    public java.math.BigDecimal promedio(long albergueId) {
        return db.queryForObject(
                "SELECT COALESCE(AVG(calificacion), 0) FROM resenas_albergue "
                        + "WHERE albergue_id = ? AND estado_moderacion = 'VISIBLE'",
                java.math.BigDecimal.class,
                albergueId);
    }

    public long contar(long albergueId) {
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM resenas_albergue WHERE albergue_id = ? AND estado_moderacion = 'VISIBLE'",
                Long.class,
                albergueId);
        return total == null ? 0 : total;
    }

    public Integer calificacionPropia(long usuarioId, long albergueId) {
        return db.query("SELECT calificacion FROM resenas_albergue WHERE usuario_id = ? AND albergue_id = ?",
                rs -> rs.next() ? rs.getInt("calificacion") : null, usuarioId, albergueId);
    }

    @Transactional
    public void crear(long usuarioId, long albergueId, CrearResenaRequest request) {
        db.update(
                "INSERT INTO resenas_albergue (usuario_id, albergue_id, calificacion, comentario) "
                        + "VALUES (?, ?, ?, ?)",
                usuarioId,
                albergueId,
                request.calificacion(),
                request.comentario().trim());
    }

    public Long obtenerAlbergueId(long resenaId) {
        return db.query("SELECT albergue_id FROM resenas_albergue WHERE id = ?",
                rs -> rs.next() ? rs.getLong("albergue_id") : null, resenaId);
    }

    public List<ResenaGestionItem> listarParaGestion(long albergueId) {
        return db.query(
                "SELECT id, calificacion, comentario, respuesta_albergue, estado_moderacion, created_at, moderada_en "
                        + "FROM resenas_albergue WHERE albergue_id = ? ORDER BY created_at DESC, id DESC",
                (rs, row) -> new ResenaGestionItem(
                        rs.getLong("id"), rs.getInt("calificacion"), rs.getString("comentario"),
                        rs.getString("respuesta_albergue"), rs.getString("estado_moderacion"),
                        toLocalDateTime(rs.getTimestamp("created_at")), toLocalDateTime(rs.getTimestamp("moderada_en"))),
                albergueId);
    }

    public boolean responder(long resenaId, long albergueId, String respuesta) {
        return db.update("UPDATE resenas_albergue SET respuesta_albergue = ?, updated_at = CURRENT_TIMESTAMP "
                        + "WHERE id = ? AND albergue_id = ?", respuesta, resenaId, albergueId) == 1;
    }

    public boolean actualizarVisibilidad(long resenaId, long albergueId, boolean visible) {
        return db.update("UPDATE resenas_albergue SET estado_moderacion = ?, moderada_en = CURRENT_TIMESTAMP, "
                        + "updated_at = CURRENT_TIMESTAMP WHERE id = ? AND albergue_id = ?",
                visible ? "VISIBLE" : "OCULTA", resenaId, albergueId) == 1;
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
