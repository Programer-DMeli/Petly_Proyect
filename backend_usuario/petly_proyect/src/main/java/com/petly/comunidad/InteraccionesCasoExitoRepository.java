package com.petly.comunidad;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InteraccionesCasoExitoRepository {

    private final JdbcTemplate db;

    public InteraccionesCasoExitoRepository(JdbcTemplate db) {
        this.db = db;
    }

    public InteraccionesCasoExitoResponse obtener(long casoId, Long usuarioId) {
        Long reacciones = db.queryForObject(
                "SELECT COUNT(*) FROM reacciones_caso_exito WHERE caso_id = ?",
                Long.class,
                casoId);
        Long comentariosTotal = db.queryForObject(
                "SELECT COUNT(*) FROM comentarios_caso_exito WHERE caso_id = ?",
                Long.class,
                casoId);
        boolean meGusta = usuarioId != null && contarMeGustaUsuario(casoId, usuarioId) > 0;
        List<ComentarioCasoExitoResponse> comentarios = db.query(
                "SELECT c.id, u.username AS autor, c.comentario, c.created_at "
                        + "FROM comentarios_caso_exito c JOIN usuarios u ON u.id = c.usuario_id "
                        + "WHERE c.caso_id = ? ORDER BY c.created_at DESC, c.id DESC",
                (rs, row) -> {
                    Timestamp creado = rs.getTimestamp("created_at");
                    return new ComentarioCasoExitoResponse(
                            rs.getLong("id"),
                            rs.getString("autor"),
                            rs.getString("comentario"),
                            creado == null ? null : creado.toLocalDateTime());
                },
                casoId);
        return new InteraccionesCasoExitoResponse(
                casoId,
                reacciones == null ? 0 : reacciones,
                meGusta,
                comentariosTotal == null ? 0 : comentariosTotal,
                comentarios);
    }

    private long contarMeGustaUsuario(long casoId, long usuarioId) {
        Long total = db.queryForObject(
                "SELECT COUNT(*) FROM reacciones_caso_exito WHERE caso_id = ? AND usuario_id = ?",
                Long.class,
                casoId,
                usuarioId);
        return total == null ? 0 : total;
    }

    public void agregarMeGusta(long casoId, long usuarioId) {
        db.update(
                "INSERT IGNORE INTO reacciones_caso_exito (caso_id, usuario_id) VALUES (?, ?)",
                casoId,
                usuarioId);
    }

    public void quitarMeGusta(long casoId, long usuarioId) {
        db.update("DELETE FROM reacciones_caso_exito WHERE caso_id = ? AND usuario_id = ?", casoId, usuarioId);
    }

    public void agregarComentario(long casoId, long usuarioId, String comentario) {
        db.update(
                "INSERT INTO comentarios_caso_exito (caso_id, usuario_id, comentario) VALUES (?, ?, ?)",
                casoId,
                usuarioId,
                comentario.trim());
    }
}
