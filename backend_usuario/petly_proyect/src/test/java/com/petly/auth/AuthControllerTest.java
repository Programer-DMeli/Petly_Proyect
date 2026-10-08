package com.petly.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

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
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

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
        req.setCorreo("repetido@petly.test");
        req.setPassword("clave12345");

        mvc.perform(desde("10.0.0.2", "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict());
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
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginBloqueadoTrasDosFallos() throws Exception {
        registrar("10.0.0.5", "bloqueo@petly.test");

        LoginRequest mala = new LoginRequest();
        mala.setCorreo("bloqueo@petly.test");
        mala.setPassword("claveMala123");

        // dos fallos y la cuenta se bloquea 5 min
        for (int i = 0; i < 2; i++) {
            mvc.perform(desde("10.0.0.5", "/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(mala)))
                    .andExpect(status().isUnauthorized());
        }

        // tercer intento: ya no entra ni con la clave correcta
        LoginRequest buena = new LoginRequest();
        buena.setCorreo("bloqueo@petly.test");
        buena.setPassword("clave12345");

        mvc.perform(desde("10.0.0.5", "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(buena)))
                .andExpect(status().isLocked());
    }
}
