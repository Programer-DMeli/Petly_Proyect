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
                "SELECT id, calificacion, comentario, created_at FROM resenas_albergue "
                        + "WHERE albergue_id = ? ORDER BY created_at DESC, id DESC",
                (rs, row) -> {
                    Timestamp creado = rs.getTimestamp("created_at");
                    return new ResenaItem(
                            rs.getLong("id"),
                            rs.getInt("calificacion"),
                            rs.getString("comentario"),
                            creado == null ? null : creado.toLocalDateTime());
                },
                albergueId);
    }

    public java.math.BigDecimal promedio(long albergueId) {
        return db.queryForObject(
                "SELECT COALESCE(AVG(calificacion), 0) FROM resenas_albergue WHERE albergue_id = ?",
                java.math.BigDecimal.class,
                albergueId);
    }

    public long contar(long albergueId) {
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM resenas_albergue WHERE albergue_id = ?",
                Long.class,
                albergueId);
        return total == null ? 0 : total;
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
}
