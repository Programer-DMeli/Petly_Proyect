package com.petly.notificaciones;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import com.petly.catalogo.CompatibilidadService;
import com.petly.common.exception.ApiException;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class AlertaCompatibilidadServiceTest {
    @Mock CompatibilidadService compatibilidad;
    @Mock AlertaCompatibilidadAlbergueRepository alertas;
    @Mock JdbcTemplate db;
    @Mock UsuarioRepository usuarios;
    @Mock PreferenciasNotificacionService preferencias;
    @Mock JavaMailSender mailSender;
    @Captor ArgumentCaptor<AlertaCompatibilidadAlbergue> alertaCaptor;

    private AlertaCompatibilidadService service;

    @BeforeEach
    void configurar() {
        service = new AlertaCompatibilidadService(
                compatibilidad, alertas, db, usuarios, preferencias, mailSender, "noreply@petly.local");
    }

    @Test
    void elUmbralEsEstrictamenteMayorQueNoventa() {
        when(alertas.findBySolicitudId(11L)).thenReturn(Optional.empty());
        when(compatibilidad.detalle(8L, 44L)).thenReturn(detalleConPorcentaje(90));

        service.registrarSiSuperaUmbral(11L, 8L, 44L);

        verify(alertas, never()).save(any());
        verify(db, never()).queryForObject(anyString(), eq(Long.class), eq(44L));
    }

    @Test
    void guardaElAvisoParaElAlberguePropietarioCuandoSuperaNoventa() {
        when(alertas.findBySolicitudId(12L)).thenReturn(Optional.empty());
        when(compatibilidad.detalle(8L, 44L)).thenReturn(detalleConPorcentaje(95));
        when(db.queryForObject(anyString(), eq(Long.class), eq(44L))).thenReturn(30L);
        when(usuarios.findById(8L)).thenReturn(Optional.of(usuario(8L, Usuario.Rol.ADOPTANTE, "Ana", "Pérez")));
        when(usuarios.findById(30L)).thenReturn(Optional.of(usuario(30L, Usuario.Rol.ALBERGUE, "Refugio", "Sur")));
        when(preferencias.obtener(30L)).thenReturn(new PreferenciaNotificacion(false, true, true));
        when(alertas.save(any(AlertaCompatibilidadAlbergue.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.registrarSiSuperaUmbral(12L, 8L, 44L);

        verify(alertas).save(alertaCaptor.capture());
        AlertaCompatibilidadAlbergue guardada = alertaCaptor.getValue();
        assertEquals(12L, guardada.getSolicitudId());
        assertEquals(30L, guardada.getAlbergueUsuarioId());
        assertEquals(8L, guardada.getAdoptanteId());
        assertEquals(44L, guardada.getMascotaId());
        assertEquals(95, guardada.getPorcentaje());
        assertEquals("Ana Pérez", guardada.getAdoptanteNombre());
        verify(mailSender, never()).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void noRepiteUnaSolicitudYaNotificada() {
        when(alertas.findBySolicitudId(13L)).thenReturn(Optional.of(
                new AlertaCompatibilidadAlbergue(13L, 30L, 8L, 44L, "Ana Pérez", "Luna", 95,
                        java.time.LocalDateTime.now())));

        service.registrarSiSuperaUmbral(13L, 8L, 44L);

        verify(compatibilidad, never()).detalle(8L, 44L);
        verify(alertas, never()).save(any());
    }

    @Test
    void unAlbergueNoPuedeMarcarLaAlertaDeOtroComoLeida() {
        when(alertas.findByIdAndAlbergueUsuarioId(7L, 30L)).thenReturn(Optional.empty());

        assertThrows(ApiException.class, () -> service.marcarLeida(30L, 7L));
        verify(alertas, never()).save(any());
    }

    private static Map<String, Object> detalleConPorcentaje(int porcentaje) {
        return Map.of("nombre", "Luna", "compatibilidad", Map.of("porcentaje", porcentaje));
    }

    private static Usuario usuario(long id, Usuario.Rol rol, String nombre, String apellido) {
        Usuario usuario = new Usuario("user" + id, "user" + id + "@petly.test", "hash", rol);
        usuario.setId(id);
        usuario.setFirstName(nombre);
        usuario.setLastName(apellido);
        return usuario;
    }
}
