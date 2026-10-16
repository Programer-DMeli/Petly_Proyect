package com.petly.auth;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petly.auth.dto.LoginRequest;
import com.petly.auth.dto.PasswordResetConfirmRequest;
import com.petly.auth.dto.PasswordResetRequest;
import com.petly.auth.dto.RegistroRequest;

/**
 * Pruebas del flujo de recuperación de contraseña (US-24).
 *
 * Se reemplaza JavaMailSender por un mock para capturar el enlace enviado
 * y extraer el token real. Requiere MariaDB en marcha.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PasswordResetFlowTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JavaMailSender mailSender;

    private final ObjectMapper json = new ObjectMapper();

    private MockHttpServletRequestBuilder desde(String ip, String uri) {
        return post(uri).with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    private void registrar(String ip, String correo, String username) throws Exception {
        RegistroRequest req = new RegistroRequest();
        req.setUsername(username);
        req.setCorreo(correo);
        req.setPassword("clave12345");
        mvc.perform(desde(ip, "/api/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    /** Solicita el reset y devuelve el token que salió en el correo mockeado. */
    private String solicitarYCapturarToken(String ip, String correo) throws Exception {
        PasswordResetRequest req = new PasswordResetRequest();
        req.setCorreo(correo);

        mvc.perform(desde(ip, "/api/auth/password/reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isOk());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        String cuerpo = captor.getValue().getText();

        int inicio = cuerpo.indexOf("token=") + "token=".length();
        int fin = cuerpo.indexOf('\n', inicio);
        String token = (fin >= 0) ? cuerpo.substring(inicio, fin) : cuerpo.substring(inicio);
        org.junit.jupiter.api.Assertions.assertEquals(22, token.length());
        org.junit.jupiter.api.Assertions.assertTrue(token.matches("[A-Za-z0-9_-]{22}"));
        return token;
    }

    @Test
    void tokenDeRecuperacionEsDeUnSoloUso() throws Exception {
        registrar("10.1.0.1", "unuso@petly.test", "unuso");
        String token = solicitarYCapturarToken("10.1.0.1", "unuso@petly.test");

        PasswordResetConfirmRequest primera = new PasswordResetConfirmRequest();
        primera.setToken(token);
        primera.setNuevaPassword("nuevaClave123");
        primera.setNuevaPasswordConfirm("nuevaClave123");

        // primer uso: OK
        mvc.perform(desde("10.1.0.1", "/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(primera)))
                .andExpect(status().isOk());

        // segundo uso del mismo token: rechazado
        mvc.perform(desde("10.1.0.1", "/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(primera)))
                .andExpect(status().isBadRequest());

        // la nueva contraseña ya funciona
        LoginRequest login = new LoginRequest();
        login.setCorreo("unuso@petly.test");
        login.setPassword("nuevaClave123");
        mvc.perform(desde("10.1.0.1", "/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void confirmacionConContrasenasDistintasFalla() throws Exception {
        registrar("10.1.0.2", "distintas@petly.test", "distintas");
        String token = solicitarYCapturarToken("10.1.0.2", "distintas@petly.test");

        PasswordResetConfirmRequest req = new PasswordResetConfirmRequest();
        req.setToken(token);
        req.setNuevaPassword("nuevaClave123");
        req.setNuevaPasswordConfirm("otraClave456");

        mvc.perform(desde("10.1.0.2", "/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void tokenInvalidoEsRechazado() throws Exception {
        PasswordResetConfirmRequest req = new PasswordResetConfirmRequest();
        req.setToken("token.invalido.falso");
        req.setNuevaPassword("nuevaClave123");
        req.setNuevaPasswordConfirm("nuevaClave123");

        mvc.perform(desde("10.1.0.3", "/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }
    @Test
    void solicitarOtroCodigoInvalidaElAnterior() throws Exception {
        registrar("10.1.0.4", "renovar@petly.test", "renovar");
        String old = solicitarYCapturarToken("10.1.0.4", "renovar@petly.test");
        org.mockito.Mockito.clearInvocations(mailSender);
        String current = solicitarYCapturarToken("10.1.0.4", "renovar@petly.test");
        org.junit.jupiter.api.Assertions.assertNotEquals(old, current);
        PasswordResetConfirmRequest req = new PasswordResetConfirmRequest();
        req.setNuevaPassword("nuevaClave123");
        req.setNuevaPasswordConfirm("nuevaClave123");
        req.setToken(old);
        mvc.perform(desde("10.1.0.4", "/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
        req.setToken(current);
        mvc.perform(desde("10.1.0.4", "/api/auth/password/reset/confirm")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

}
