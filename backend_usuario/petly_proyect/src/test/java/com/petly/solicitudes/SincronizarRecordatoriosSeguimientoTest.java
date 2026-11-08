package com.petly.solicitudes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SincronizarRecordatoriosSeguimientoTest {

    private static final String FEED_URL =
            "http://django.test/api/contratos/reportes/feed-recordatorios/";

    @Test
    void creaRecordatorioPendienteParaSeguimientoProgramado() {
        var guardados = new ArrayList<RecordatorioSeguimiento>();
        RecordatorioSeguimientoRepository repository = repository(Map.of(), guardados);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(FEED_URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withSuccess(feed(27, false), MediaType.APPLICATION_JSON));

        sincronizador(restTemplate, repository).sincronizar();

        assertEquals(1, guardados.size());
        RecordatorioSeguimiento guardado = guardados.get(0);
        assertEquals(27L, guardado.getSeguimientoId());
        assertEquals(11L, guardado.getAdoptanteId());
        assertEquals(LocalDateTime.of(2026, 11, 10, 12, 0), guardado.getFechaLimite());
        assertEquals(RecordatorioSeguimiento.Estado.PENDIENTE, guardado.getEstado());
        server.verify();
    }

    @Test
    void reporteEntregadoCompletaRecordatorioExistente() {
        RecordatorioSeguimiento existente = new RecordatorioSeguimiento(
                28L, 11L, LocalDateTime.of(2026, 11, 10, 12, 0));
        var guardados = new ArrayList<RecordatorioSeguimiento>();
        RecordatorioSeguimientoRepository repository = repository(Map.of(28L, existente), guardados);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(FEED_URL))
                .andRespond(withSuccess(feed(28, true), MediaType.APPLICATION_JSON));

        sincronizador(restTemplate, repository).sincronizar();

        assertEquals(RecordatorioSeguimiento.Estado.COMPLETADO, existente.getEstado());
        assertEquals(java.util.List.of(existente), guardados);
        server.verify();
    }

    @Test
    void reporteEntregadoNoCreaUnRecordatorioPendiente() {
        var guardados = new ArrayList<RecordatorioSeguimiento>();
        RecordatorioSeguimientoRepository repository = repository(Map.of(), guardados);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(FEED_URL))
                .andRespond(withSuccess(feed(29, true), MediaType.APPLICATION_JSON));

        sincronizador(restTemplate, repository).sincronizar();

        assertEquals(0, guardados.size());
        server.verify();
    }

    @SuppressWarnings("unchecked")
    private static RecordatorioSeguimientoRepository repository(
            Map<Long, RecordatorioSeguimiento> existentes,
            java.util.List<RecordatorioSeguimiento> guardados) {
        return (RecordatorioSeguimientoRepository) Proxy.newProxyInstance(
                RecordatorioSeguimientoRepository.class.getClassLoader(),
                new Class<?>[] {RecordatorioSeguimientoRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findBySeguimientoId" -> Optional.ofNullable(existentes.get((Long) args[0]));
                    case "save" -> {
                        RecordatorioSeguimiento recordatorio = (RecordatorioSeguimiento) args[0];
                        guardados.add(recordatorio);
                        yield recordatorio;
                    }
                    case "toString" -> "RecordatorioSeguimientoRepository test fake";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static SincronizarRecordatoriosSeguimiento sincronizador(
            RestTemplate restTemplate, RecordatorioSeguimientoRepository repository) {
        return new SincronizarRecordatoriosSeguimiento(
                restTemplate, repository, "http://django.test", "test-secret");
    }

    private static String feed(long id, boolean reporteEnviado) {
        return "[{\"id\":" + id
                + ",\"adoptante_spring_id\":11"
                + ",\"programado_para\":\"2026-11-10T12:00:00-05:00\""
                + ",\"estado\":\"PROGRAMADO\""
                + ",\"reporte_enviado\":" + reporteEnviado
                + ",\"expediente_cerrado\":false}]";
    }
}
