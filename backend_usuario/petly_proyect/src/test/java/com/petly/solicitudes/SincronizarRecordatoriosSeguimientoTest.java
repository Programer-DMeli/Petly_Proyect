package com.petly.solicitudes;

import com.petly.notificaciones.PreferenciaNotificacion;
import com.petly.notificaciones.PreferenciasNotificacionService;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SincronizarRecordatoriosSeguimientoTest {
    private static final String BASE = "http://django.test";
    private static final String FEED = BASE + "/api/contratos/reportes/feed-recordatorios/";
    private static final LocalDateTime LIMITE = LocalDateTime.of(2026, 12, 10, 12, 0);

    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer django = MockRestServiceServer.bindTo(http).build();
    private final RecordatorioSeguimientoRepository repositorio = mock(RecordatorioSeguimientoRepository.class);
    private final JavaMailSender correo = mock(JavaMailSender.class);
    private final HashMap<Clave, RecordatorioSeguimiento> guardados = new HashMap<>();
    private final SincronizarRecordatoriosSeguimiento sincronizador;

    SincronizarRecordatoriosSeguimientoTest() {
        when(repositorio.findBySeguimientoIdAndAnticipacionDias(anyLong(), anyInt()))
                .thenAnswer(invocacion -> Optional.ofNullable(guardados.get(
                        new Clave(invocacion.getArgument(0), invocacion.getArgument(1)))));
        when(repositorio.bloquearPorSeguimientoYAnticipacion(anyLong(), anyInt()))
                .thenAnswer(invocacion -> Optional.ofNullable(guardados.get(
                        new Clave(invocacion.getArgument(0), invocacion.getArgument(1)))));
        when(repositorio.save(any(RecordatorioSeguimiento.class))).thenAnswer(invocacion -> {
            RecordatorioSeguimiento fila = invocacion.getArgument(0);
            guardados.put(new Clave(fila.getSeguimientoId(), fila.getAnticipacionDias()), fila);
            return fila;
        });
        var preferencias = mock(PreferenciasNotificacionService.class);
        when(preferencias.obtener(11L)).thenReturn(PreferenciaNotificacion.porDefecto());
        var usuarios = mock(UsuarioRepository.class);
        when(usuarios.findById(11L)).thenReturn(Optional.of(
                new Usuario("Ana", "ana@example.test", "sin-uso", Usuario.Rol.ADOPTANTE)));
        var remitente = new EnviarRecordatoriosSeguimiento(
                repositorio, usuarios, preferencias, correo, "petly@example.test", "http://petly.test/reportes");
        sincronizador = new SincronizarRecordatoriosSeguimiento(http, repositorio, remitente, BASE, "test-secret");
    }

    @Test
    void entregaUnaVezPorAnticipacionYOmiteAvisosFueraDeLaVentana() {
        sincronizar(LIMITE.minusDays(3).minusSeconds(1), 27, "PROGRAMADO", false, false);
        assertEquals(0, guardados.size());
        verifyNoInteractions(correo);

        sincronizar(LIMITE.minusDays(3), 27, "PROGRAMADO", false, true);
        sincronizar(LIMITE.minusDays(3).plusMinutes(10), 27, "PROGRAMADO", false, true);
        assertEquals(1, guardados.size());
        var primerAviso = guardados.get(new Clave(27L, 3));
        assertEquals(LIMITE, primerAviso.getFechaLimite());
        assertEquals(LIMITE.minusDays(3), primerAviso.getEnviadoEn());
        verify(correo).send(any(SimpleMailMessage.class));

        sincronizar(LIMITE.minusDays(2), 27, "PROGRAMADO", false, true);
        sincronizar(LIMITE.minusDays(2), 28, "PROGRAMADO", false, true);
        assertFalse(guardados.containsKey(new Clave(28L, 3)));
        sincronizar(LIMITE.minusDays(1), 27, "PROGRAMADO", false, true);
        sincronizar(LIMITE.minusDays(1).plusMinutes(5), 27, "PROGRAMADO", false, true);
        sincronizar(LIMITE, 27, "PROGRAMADO", false, false);
        assertEquals(2, guardados.size());
        assertEquals(LIMITE.minusDays(1), guardados.get(new Clave(27L, 1)).getEnviadoEn());
        var salientes = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(correo, times(2)).send(salientes.capture());
        for (var mensaje : salientes.getAllValues()) {
            assertNotNull(mensaje.getText());
            org.junit.jupiter.api.Assertions.assertTrue(mensaje.getText().contains("entrega=27#seguimiento-27"));
        }
    }

    @Test
    void entregarOCancelarConservaElEnvioAnteriorYDetieneElSiguiente() {
        for (long id : new long[]{28, 29}) {
            sincronizar(LIMITE.minusDays(3), id, "PROGRAMADO", false, true);
            String estado = id == 28 ? "REALIZADO" : "CANCELADO";
            boolean reporteEnviado = id == 28;
            sincronizar(LIMITE.minusDays(1), id, estado, reporteEnviado, false);
            sincronizar(LIMITE.minusDays(1).plusMinutes(1), id, estado, reporteEnviado, false);
            var primero = guardados.get(new Clave(id, 3));
            assertEquals(reporteEnviado ? RecordatorioSeguimiento.Estado.COMPLETADO
                    : RecordatorioSeguimiento.Estado.CANCELADO, primero.getEstado());
            assertEquals(LIMITE.minusDays(3), primero.getEnviadoEn());
            assertFalse(guardados.containsKey(new Clave(id, 1)));
        }
        verify(correo, times(2)).send(any(SimpleMailMessage.class));
    }

    private void sincronizar(LocalDateTime ahora, long id, String estado, boolean enviado, boolean activo) {
        django.reset();
        django.expect(requestTo(FEED))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Secret", "test-secret"))
                .andRespond(withSuccess("""
                        [{"id":%s,"adoptante_spring_id":11,"programado_para":"2026-12-10T17:00:00Z",
                          "estado":"%s","reporte_enviado":%s,"expediente_cerrado":false,
                          "recordatorio_activo":%s,"mascota_nombre":"Luna"}]
                        """.formatted(id, estado, enviado, activo), MediaType.APPLICATION_JSON));
        sincronizador.sincronizar(ahora);
        django.verify();
    }

    private record Clave(long seguimientoId, int anticipacionDias) {}
}
