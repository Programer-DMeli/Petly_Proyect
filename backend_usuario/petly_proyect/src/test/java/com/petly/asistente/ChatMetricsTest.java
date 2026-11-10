package com.petly.asistente;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChatMetricsTest {
    private final JdbcTemplate db = mock(JdbcTemplate.class);
    private final ChatMetrics metrics = new ChatMetrics(db);

    @Test void classifiesOneTopicAndCountsItOnceDespiteRepeatedKeywords() {
        metrics.record("¿Qué VACUNAS, ALIMENTACIÓN y cuidados necesita?");
        verify(db).update(anyString(), eq("cuidados"));
        verifyNoMoreInteractions(db);
    }

    @Test void classifiesSeveralTopicsIncludingAccentedUppercaseWords() {
        metrics.record("¿Cómo lleno mi PERFIL para ADOPTAR y qué VACUNAS necesita?");
        for (String topic : List.of("perfil_cuestionario", "proceso_adopcion", "cuidados")) {
            verify(db).update(anyString(), eq(topic));
        }
        verifyNoMoreInteractions(db);
    }

    @Test void classifiesUnmatchedQuestionsAsOtherQueries() {
        metrics.record("¡Buenos días!");
        verify(db).update(anyString(), eq("otras_consultas"));
        verifyNoMoreInteractions(db);
    }

    @Test void readsPersistedCountsAndDefaultsMissingCountsToZero() {
        when(db.queryForObject(anyString(), eq(Long.class), eq("perfil_cuestionario"))).thenReturn(7L);
        when(db.queryForObject(anyString(), eq(Long.class), eq("proceso_adopcion"))).thenReturn(3L);
        when(db.queryForObject(anyString(), eq(Long.class), eq("cuidados"))).thenReturn(0L);
        when(db.queryForObject(anyString(), eq(Long.class), eq("otras_consultas"))).thenReturn(null);

        var snapshot = metrics.snapshot();
        assertEquals(Map.of("perfil_cuestionario", 7L, "proceso_adopcion", 3L,
                "cuidados", 0L, "otras_consultas", 0L), snapshot.get("temas"));
        assertEquals("base_de_datos", snapshot.get("persistencia"));
        for (String topic : List.of("perfil_cuestionario", "proceso_adopcion", "cuidados", "otras_consultas")) {
            verify(db).queryForObject(anyString(), eq(Long.class), eq(topic));
        }
        verifyNoMoreInteractions(db);
    }

    @Test void sqlAndParametersContainOnlyAggregatedTopicsNeverQuestionOrIdentity() {
        String identity = "persona-privada@example.test";
        String question = "Mi perfil para adoptar: " + identity + "; DNI 12345678; vacunas";
        metrics.record(question);
        metrics.snapshot();

        var topics = List.of("perfil_cuestionario", "proceso_adopcion", "cuidados", "otras_consultas");
        var calls = mockingDetails(db).getInvocations();
        assertEquals(7, calls.size());
        for (var call : calls) {
            String sql = call.getArgument(0);
            Object[] rawArguments = call.getRawArguments();
            Object[] parameters = (Object[]) rawArguments[rawArguments.length - 1];
            assertEquals(1, parameters.length);
            assertTrue(topics.contains(parameters[0]));
            assertFalse(sql.contains(question));
            assertFalse(sql.contains(identity));
            assertFalse(sql.contains("12345678"));
            if (call.getMethod().getName().equals("update")) {
                assertTrue(sql.startsWith("INSERT INTO asistente_metricas_diarias(fecha, tema, consultas)"));
                assertTrue(sql.contains("VALUES (CURRENT_DATE, ?, 1)"));
                assertTrue(sql.contains("ON DUPLICATE KEY UPDATE consultas = consultas + 1"));
            } else {
                assertEquals("SELECT COALESCE(SUM(consultas), 0) FROM asistente_metricas_diarias WHERE tema=?", sql);
            }
        }
    }
}
