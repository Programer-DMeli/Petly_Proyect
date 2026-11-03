package com.petly.asistente;

import java.text.Normalizer;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Service;

/** Aggregate topics only: no identities, message texts or transcripts are retained. */
@Service
public class ChatMetrics {
    private final Instant since = Instant.now();
    private final Map<String, LongAdder> topics = new LinkedHashMap<>();

    public ChatMetrics() {
        for (String topic : new String[]{"perfil_cuestionario", "proceso_adopcion", "cuidados", "otras_consultas"}) {
            topics.put(topic, new LongAdder());
        }
    }

    public void record(String question) {
        String text = Normalizer.normalize(question.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        boolean identified = false;
        if (contains(text, "cuestionario", "perfil", "vivienda", "departamento", "horas fuera", "estilo de vida")) {
            topics.get("perfil_cuestionario").increment();
            identified = true;
        }
        if (contains(text, "adopt", "requisito", "documento", "contrato", "albergue", "solicitud", "dni")) {
            topics.get("proceso_adopcion").increment();
            identified = true;
        }
        if (contains(text, "cuid", "alimenta", "comida", "vacun", "veterin", "paseo", "raza", "enferm", "salud", "esteriliz")) {
            topics.get("cuidados").increment();
            identified = true;
        }
        if (!identified) topics.get("otras_consultas").increment();
    }

    private boolean contains(String text, String... terms) {
        return Arrays.stream(terms).anyMatch(text::contains);
    }

    public Map<String, Object> snapshot() {
        Map<String, Long> counts = new LinkedHashMap<>();
        topics.forEach((topic, count) -> counts.put(topic, count.sum()));
        return Map.of("desde", since.toString(), "persistencia", "memoria_del_servidor",
                "metodo", "clasificacion_por_temas; una consulta puede incluir varios temas", "temas", counts);
    }
}
