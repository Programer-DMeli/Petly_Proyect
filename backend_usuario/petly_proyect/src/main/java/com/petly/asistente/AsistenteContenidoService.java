package com.petly.asistente;

import static com.petly.asistente.AsistenteContenidoDtos.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AsistenteContenidoService {
    private final JdbcTemplate db;

    public AsistenteContenidoService(JdbcTemplate db) {
        this.db = db;
    }

    public List<CategoriaPreguntasFrecuentes> preguntasFrecuentes() {
        Map<String, CategoriaBuilder> categorias = new LinkedHashMap<>();
        db.query("SELECT id, nombre FROM asistente_faq_categoria WHERE activa=TRUE ORDER BY orden, nombre",
                rs -> {
                    categorias.put(rs.getString("id"),
                            new CategoriaBuilder(rs.getString("id"), rs.getString("nombre")));
                });

        db.query("""
                SELECT p.id, p.categoria_id, p.pregunta, p.respuesta
                FROM asistente_pregunta_frecuente p
                JOIN asistente_faq_categoria c ON c.id=p.categoria_id
                WHERE p.activa=TRUE AND c.activa=TRUE
                ORDER BY c.orden, p.orden, p.id
                """, rs -> {
                    CategoriaBuilder categoria = categorias.get(rs.getString("categoria_id"));
                    if (categoria != null) {
                        categoria.preguntas.add(new PreguntaFrecuente(
                                rs.getString("id"), rs.getString("pregunta"), rs.getString("respuesta")));
                    }
                });

        return categorias.values().stream().map(CategoriaBuilder::build).toList();
    }

    public RespuestaCuidados cuidados(String especie, String raza) {
        String especieNormalizada = especie.trim().toLowerCase(Locale.ROOT);
        String razaNormalizada = raza == null ? "" : raza.trim().toLowerCase(Locale.ROOT);

        List<ConsejoCuidado> consejos = db.query("""
                SELECT categoria, titulo, contenido
                FROM asistente_consejo_cuidado
                WHERE activo=TRUE AND especie=? AND (raza='' OR (?<>'' AND LOWER(raza)=?))
                ORDER BY CASE WHEN raza='' THEN 0 ELSE 1 END, orden, id
                """, (rs, rowNum) -> new ConsejoCuidado(
                rs.getString("categoria"), rs.getString("titulo"), rs.getString("contenido")),
                especieNormalizada, razaNormalizada, razaNormalizada);

        boolean especifica = !razaNormalizada.isBlank()
                && db.queryForObject("SELECT COUNT(*) FROM asistente_consejo_cuidado "
                                + "WHERE activo=TRUE AND especie=? AND LOWER(raza)=?",
                        Integer.class, especieNormalizada, razaNormalizada) > 0;
        String mensaje = especifica
                ? "Incluimos recomendaciones generales y específicas para la raza consultada."
                : razaNormalizada.isBlank()
                        ? "Estas recomendaciones corresponden a la especie."
                        : "No hay recomendaciones específicas para esa raza; mostramos las disponibles para la especie.";
        return new RespuestaCuidados(especieNormalizada, raza == null ? "" : raza.trim(), especifica, mensaje, consejos);
    }

    public List<GuiaCuestionario> guiaCuestionario() {
        return db.query("""
                SELECT campo, pregunta, orientacion, orden
                FROM asistente_guia_cuestionario
                WHERE activa=TRUE
                ORDER BY orden, campo
                """, (rs, rowNum) -> new GuiaCuestionario(
                rs.getString("campo"), rs.getString("pregunta"),
                rs.getString("orientacion"), rs.getInt("orden")));
    }

    private static final class CategoriaBuilder {
        private final String id;
        private final String nombre;
        private final List<PreguntaFrecuente> preguntas = new ArrayList<>();

        private CategoriaBuilder(String id, String nombre) {
            this.id = id;
            this.nombre = nombre;
        }

        private CategoriaPreguntasFrecuentes build() {
            return new CategoriaPreguntasFrecuentes(id, nombre, List.copyOf(preguntas));
        }
    }
}
