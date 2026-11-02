package com.petly.notificaciones;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * Pruebas de las preferencias de canal de notificación (US-45).
 * Cada test registra y entra desde su propia IP para no chocar con el rate-limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PreferenciasNotificacionControllerTest {

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private MockHttpServletRequestBuilder desde(String ip, String uri) {
        return post(uri).with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    private String token(String ip, String sufijo) throws Exception {
        RegistroRequest registro = new RegistroRequest();
        registro.setUsername("pref" + sufijo);
        registro.setCorreo("pref" + sufijo + "@petly.test");
        registro.setPassword("clave12345");
        mvc.perform(desde(ip, "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(registro)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest();
        login.setCorreo("pref" + sufijo + "@petly.test");
        login.setPassword("clave12345");
        String cuerpo = mvc.perform(desde(ip, "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo).get("token").asText();
    }

    private String canales(boolean email, boolean whatsapp, boolean push) throws Exception {
        return json.writeValueAsString(new PreferenciaNotificacion(email, whatsapp, push));
    }

    @Test
    void sinRegistroPrevioTodosLosCanalesEstanActivos() throws Exception {
        String token = token("10.5.0.1", "uno");

        mvc.perform(get("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.push").value(true));
    }

    @Test
    void guardaYDevuelveLosCanalesElegidos() throws Exception {
        String token = token("10.5.0.2", "dos");

        mvc.perform(put("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(canales(false, true, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(false))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.push").value(false));

        mvc.perform(get("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(false))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.push").value(false));
    }

    @Test
    void guardarDosVecesActualizaLaMismaFila() throws Exception {
        String token = token("10.5.0.3", "tres");

        mvc.perform(put("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(canales(false, false, true)))
                .andExpect(status().isOk());

        mvc.perform(put("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(canales(true, false, false)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.whatsapp").value(false))
                .andExpect(jsonPath("$.push").value(false));
    }

    @Test
    void cadaUsuarioConservaSusPropiasPreferencias() throws Exception {
        String tokenUno = token("10.5.0.4", "cuatro");
        String tokenDos = token("10.5.0.5", "cinco");

        mvc.perform(put("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + tokenUno)
                .contentType(MediaType.APPLICATION_JSON)
                .content(canales(false, false, false)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + tokenDos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.push").value(true));
    }

    @Test
    void rechazaCanalesIncompletos() throws Exception {
        String token = token("10.5.0.6", "seis");

        mvc.perform(put("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":true}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/notificaciones/preferencias")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(true))
                .andExpect(jsonPath("$.whatsapp").value(true))
                .andExpect(jsonPath("$.push").value(true));
    }
}
