package com.petly.autorizacion;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import com.petly.autorizacion.dto.AutorizacionRequest;

/**
 * Pruebas de la API de autorizacion de consulta (US-18).
 *
 * Registro y login entran desde una IP propia por test para no chocar con
 * el rate-limit. Con @Transactional todo se revierte al final.
 * Requiere MariaDB en marcha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AutorizacionControllerTest {

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate db;

    @org.junit.jupiter.api.BeforeEach
    void alberguesDePrueba() {
        db.update("MERGE INTO albergues_albergue (id,nombre,direccion,activo) KEY(id) VALUES (1,'Refugio prueba','Dirección prueba',TRUE)");
        db.update("MERGE INTO albergues_albergue (id,nombre,direccion,activo) KEY(id) VALUES (3,'Refugio prueba','Dirección prueba',TRUE)");
        db.update("MERGE INTO albergues_albergue (id,nombre,direccion,activo) KEY(id) VALUES (5,'Refugio prueba','Dirección prueba',TRUE)");
        db.update("MERGE INTO albergues_albergue (id,nombre,direccion,activo) KEY(id) VALUES (8,'Refugio prueba','Dirección prueba',TRUE)");
    }

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

    /** Registra un adoptante y devuelve su token de acceso. */
    private String tokenDeAdoptante(String ip, String sufijo) throws Exception {
        RegistroRequest registro = new RegistroRequest();
        registro.setUsername("autz" + sufijo);
        registro.setCorreo("autz" + sufijo + "@petly.test");
        registro.setPassword("clave12345");
        mvc.perform(desde(ip, "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(registro)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest();
        login.setCorreo("autz" + sufijo + "@petly.test");
        login.setPassword("clave12345");
        String cuerpo = mvc.perform(desde(ip, "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    private String body(long albergueId) throws Exception {
        return json.writeValueAsString(new AutorizacionRequest(albergueId));
    }

    @Test
    void autorizaYLista() throws Exception {
        String token = tokenDeAdoptante("10.3.0.1", "uno");

        mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(5L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.albergueId").value(5))
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        mvc.perform(get("/api/autorizaciones")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].albergueId").value(5));
    }

    @Test
    void revocarDesactivaLaAutorizacion() throws Exception {
        String token = tokenDeAdoptante("10.3.0.2", "dos");

        mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(8L)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/autorizaciones/8")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(get("/api/autorizaciones")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("REVOCADA"));
    }

    @Test
    void revocarInexistenteDaNotFound() throws Exception {
        String token = tokenDeAdoptante("10.3.0.3", "tres");

        mvc.perform(delete("/api/autorizaciones/99")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void reactivaTrasRevocar() throws Exception {
        String token = tokenDeAdoptante("10.3.0.4", "cuatro");

        mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(3L)))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/autorizaciones/3")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(3L)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("ACTIVA"));

        mvc.perform(get("/api/autorizaciones")
                .header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$[0].estado").value("ACTIVA"));
    }

    @Test
    void aislamientoEntreCuentas() throws Exception {
        String tokenA = tokenDeAdoptante("10.3.0.5", "cinco");
        String tokenB = tokenDeAdoptante("10.3.0.6", "seis");

        mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + tokenA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(1L)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/autorizaciones")
                .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void sinTokenRechaza() throws Exception {
        mvc.perform(get("/api/autorizaciones"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void albergueIdInvalidoDaBadRequest() throws Exception {
        String token = tokenDeAdoptante("10.3.0.7", "siete");

        mvc.perform(post("/api/autorizaciones")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(0L)))
                .andExpect(status().isBadRequest());
    }
}
