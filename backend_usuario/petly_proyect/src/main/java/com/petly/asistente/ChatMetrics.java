package com.petly.asistente;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persiste conteos agregados por tema; nunca almacena el texto ni la identidad del usuario. */
@Service
public class ChatMetrics {
    private final JdbcTemplate db;

    public ChatMetrics(JdbcTemplate db) { this.db = db; }

    @Transactional
    public void record(String question) {
        String text = Normalizer.normalize(question.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        Map<String, Boolean> detected = new LinkedHashMap<>();
        detected.put("perfil_cuestionario", contains(text, "cuestionario", "perfil", "vivienda", "departamento", "horas fuera", "estilo de vida"));
        detected.put("proceso_adopcion", contains(text, "adopt", "requisito", "documento", "contrato", "albergue", "solicitud", "dni"));
        detected.put("cuidados", contains(text, "cuid", "alimenta", "comida", "vacun", "veterin", "paseo", "raza", "enferm", "salud", "esteriliz"));
        if (detected.values().stream().noneMatch(Boolean::booleanValue)) detected.put("otras_consultas", true);
        detected.forEach((topic, yes) -> {
            if (yes) db.update("INSERT INTO asistente_metricas_diarias(fecha, tema, consultas) VALUES (CURRENT_DATE, ?, 1) "
                    + "ON DUPLICATE KEY UPDATE consultas = consultas + 1", topic);
        });
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String topic : new String[]{"perfil_cuestionario", "proceso_adopcion", "cuidados", "otras_consultas"}) {
            Long count = db.queryForObject("SELECT COALESCE(SUM(consultas), 0) FROM asistente_metricas_diarias WHERE tema=?", Long.class, topic);
            counts.put(topic, count == null ? 0L : count);
        }
        return Map.of("persistencia", "base_de_datos", "metodo", "conteo_agregado_por_tema_sin_texto_ni_identidad", "temas", counts);
    }

    private static boolean contains(String text, String... terms) {
        return Arrays.stream(terms).anyMatch(text::contains);
    }
}
