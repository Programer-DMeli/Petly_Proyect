package com.petly.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petly.auth.dto.LoginRequest;
import com.petly.auth.dto.RegistroRequest;

/**
 * Pruebas del registro y login (US-21).
 *
 * Cada test entra desde su propia IP para no chocar con el rate-limit de
 * 10 por minuto. Con @Transactional todo se revierte al final y la BD
 * queda como estaba. Requiere MariaDB en marcha.
 */
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(AuthTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private com.petly.usuarios.UsuarioRepository usuarios;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void registroMobileGuardaNombreYAceptacion() throws Exception {
        RegistroRequest req = new RegistroRequest();
        req.setCorreo("camila-mobile@petly.test");
        req.setPassword("clave12345");
        req.setFirstName("Camila Morales");
        req.setAceptoTerminos(true);
        mvc.perform(desde("10.5.0.1", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());
        usuarios.flush();
        entityManager.clear();
        var usuario = usuarios.findByCorreo("camila-mobile@petly.test").orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Camila Morales", usuario.getFirstName());
        org.junit.jupiter.api.Assertions.assertTrue(usuario.getAceptoTerminos());
        org.junit.jupiter.api.Assertions.assertNotNull(usuario.getFechaAceptacionTerminos());
    }

    @Test
    void registroAnteriorNoInventaAceptacion() throws Exception {
        registrar("10.5.0.2", "sin-consentimiento@petly.test");
        usuarios.flush();
        entityManager.clear();
        var usuario = usuarios.findByCorreo("sin-consentimiento@petly.test").orElseThrow();
        org.junit.jupiter.api.Assertions.assertFalse(usuario.getAceptoTerminos());
        org.junit.jupiter.api.Assertions.assertNull(usuario.getFechaAceptacionTerminos());
    }

    @Test
    void registroRechazaNombreQueNoCabeEnLaBase() throws Exception {
        RegistroRequest req = new RegistroRequest();
        req.setCorreo("nombre-largo@petly.test");
        req.setPassword("clave12345");
        req.setFirstName("a".repeat(51));
        mvc.perform(desde("10.5.0.3", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ObjectMapper no viene como bean, lo armamos nosotros
    private final ObjectMapper json = new ObjectMapper();

    private MockHttpServletRequestBuilder desde(String ip, String uri) {
        return post(uri).with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    private void registrar(String ip, String correo) throws Exception {
        RegistroRequest req = new RegistroRequest();
        req.setUsername(correo.split("@")[0]);
        req.setCorreo(correo);
        req.setPassword("clave12345");
        mvc.perform(desde(ip, "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void registroCuentaNueva() throws Exception {
        RegistroRequest req = new RegistroRequest();
        req.setUsername("nuevo");
        req.setCorreo("nuevo@petly.test");
        req.setPassword("clave12345");

        mvc.perform(desde("10.0.0.1", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value("nuevo@petly.test"))
                .andExpect(jsonPath("$.rol").value("ADOPTANTE"));
    }

    @Test
    void registroCorreoRepetido() throws Exception {
        registrar("10.0.0.2", "repetido@petly.test");

        RegistroRequest req = new RegistroRequest();
        req.setUsername("repetido");
        req.setCorreo("repetido@petly.test");
        req.setPassword("clave12345");

        mvc.perform(desde("10.0.0.2", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void registroUsuarioRepetido() throws Exception {
        registrar("10.0.0.2", "dupuser@petly.test");

        RegistroRequest req = new RegistroRequest();
        req.setUsername("dupuser");
        req.setCorreo("dupuser@petlyprueba.test");
        req.setPassword("clave12345");

        mvc.perform(desde("10.0.0.2", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflicto"));
    }

    @Test
    void registroIgnoraRolAdminDelCliente() throws Exception {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", "roltest");
        body.put("correo", "roltest@petly.test");
        body.put("password", "clave12345");
        body.put("rol", "ADMIN");

        mvc.perform(desde("10.0.0.2", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value("roltest@petly.test"))
                .andExpect(jsonPath("$.rol").value("ADOPTANTE"));
    }

    @Test
    void loginConClaveCorrecta() throws Exception {
        registrar("10.0.0.3", "entra@petly.test");

        LoginRequest req = new LoginRequest();
        req.setCorreo("entra@petly.test");
        req.setPassword("clave12345");

        mvc.perform(desde("10.0.0.3", "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.correo").value("entra@petly.test"));
    }

    @Test
    void loginConClaveMala() throws Exception {
        registrar("10.0.0.4", "malapass@petly.test");

        LoginRequest req = new LoginRequest();
        req.setCorreo("malapass@petly.test");
        req.setPassword("claveMala123");

        mvc.perform(desde("10.0.0.4", "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void loginBloqueadoTrasDosFallos() throws Exception {
        registrar("10.0.0.5", "bloqueo@petly.test");

        LoginRequest mala = new LoginRequest();
        mala.setCorreo("bloqueo@petly.test");
        mala.setPassword("claveMala123");

        // primer fallo devuelve 401 y el segundo bloquea la cuenta durante 1 min;
        // el bloqueo se reporta como 423 desde el siguiente intento
        for (int i = 0; i < 2; i++) {
            mvc.perform(desde("10.0.0.5", "/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(mala)))
                    .andExpect(i == 0 ? status().isUnauthorized() : status().isLocked());
        }

        // tercer intento: ya no entra ni con la clave correcta
        LoginRequest buena = new LoginRequest();
        buena.setCorreo("bloqueo@petly.test");
        buena.setPassword("clave12345");

        mvc.perform(desde("10.0.0.5", "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(buena)))
                .andExpect(status().isLocked())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(423))
                .andExpect(jsonPath("$.message", containsString("bloqueada")));
    }
}
