package com.petly.solicitudes;

import com.petly.auth.JwtService;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecordatoriosAdoptanteControllerTest {
    private static final String URL = "/api/seguimientos/adoptante/recordatorios";
    private static final LocalDateTime LIMITE = LocalDateTime.of(2026, 11, 10, 12, 0);

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired JwtService jwt;
    @Autowired RecordatorioSeguimientoRepository recordatorios;

    @Test
    void listaSoloLosReportesPendientesDelTitularSinRomperElHistorial() throws Exception {
        var titular = usuario("titular");
        var otro = usuario("otro");
        var enviado = new RecordatorioSeguimiento(102L, titular.getId(), LIMITE.plusDays(1));
        enviado.marcarEnviado(LIMITE.minusDays(1));
        var completado = new RecordatorioSeguimiento(103L, titular.getId(), LIMITE.minusDays(1));
        completado.completar();
        var cancelado = new RecordatorioSeguimiento(104L, titular.getId(), LIMITE.minusDays(2));
        cancelado.cancelar();
        recordatorios.saveAll(List.of(
                new RecordatorioSeguimiento(101L, titular.getId(), LIMITE),
                enviado, completado, cancelado,
                new RecordatorioSeguimiento(105L, otro.getId(), LIMITE.minusDays(3))));

        mvc.perform(get(URL).param("pendientes", "true"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(URL).param("pendientes", "true").header("Authorization", "Bearer " + jwt.generarToken(titular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].seguimiento_id").value(101))
                .andExpect(jsonPath("$[0].fecha_limite").value("2026-11-10T12:00:00"))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[1].seguimiento_id").value(102))
                .andExpect(jsonPath("$[1].estado").value("ENVIADO"));
        mvc.perform(get(URL).header("Authorization", "Bearer " + jwt.generarToken(titular)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));
    }

    private Usuario usuario(String nombre) {
        return usuarios.save(new Usuario(nombre, nombre + "-recordatorios@example.test",
                "unused-test-password", Usuario.Rol.ADOPTANTE));
    }
}
