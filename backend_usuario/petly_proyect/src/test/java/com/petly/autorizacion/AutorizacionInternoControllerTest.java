package com.petly.autorizacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.petly.usuarios.Usuario;
import com.petly.usuarios.repository.UsuarioRepository;

/**
 * Pruebas del endpoint interno de vigencia (US-18).
 * Django consulta aqui con el secreto de servicio (X-Internal-Secret).
 * Requiere MariaDB en marcha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutorizacionInternoControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AutorizacionRepository autorizaciones;

    @Autowired
    private UsuarioRepository usuarios;

    @Value("${interno.api-secret}")
    private String secreto;

    private Usuario adoptante(String sufijo) {
        return usuarios.save(new Usuario(
                "interno" + sufijo,
                "interno" + sufijo + "@petly.test",
                "hash",
                Usuario.Rol.ADOPTANTE));
    }

    @Test
    void autorizaCuandoEstaActiva() throws Exception {
        Usuario u = adoptante("activa");
        autorizaciones.save(new Autorizacion(u.getId(), 5L));

        mvc.perform(get("/api/interno/autorizaciones")
                .header("X-Internal-Secret", secreto)
                .param("adoptanteId", u.getId().toString())
                .param("albergueId", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(true));
    }

    @Test
    void deniegaSinRegistro() throws Exception {
        Usuario u = adoptante("sinauth");

        mvc.perform(get("/api/interno/autorizaciones")
                .header("X-Internal-Secret", secreto)
                .param("adoptanteId", u.getId().toString())
                .param("albergueId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(false));
    }

    @Test
    void deniegaTrasRevocar() throws Exception {
        Usuario u = adoptante("revocada");
        Autorizacion autorizacion = new Autorizacion(u.getId(), 9L);
        autorizacion.revocar();
        autorizaciones.save(autorizacion);

        mvc.perform(get("/api/interno/autorizaciones")
                .header("X-Internal-Secret", secreto)
                .param("adoptanteId", u.getId().toString())
                .param("albergueId", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autorizado").value(false));
    }

    @Test
    void sinSecretoRechaza() throws Exception {
        mvc.perform(get("/api/interno/autorizaciones")
                .param("adoptanteId", "1")
                .param("albergueId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void secretoIncorrectoRechaza() throws Exception {
        mvc.perform(get("/api/interno/autorizaciones")
                .header("X-Internal-Secret", "secreto-malo")
                .param("adoptanteId", "1")
                .param("albergueId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void faltanParametrosDaBadRequest() throws Exception {
        mvc.perform(get("/api/interno/autorizaciones")
                .header("X-Internal-Secret", secreto)
                .param("adoptanteId", "1"))
                .andExpect(status().isBadRequest());
    }
}
