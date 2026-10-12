package com.petly.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petly.auth.JwtService;
import com.petly.auth.dto.LoginRequest;
import com.petly.auth.dto.RegistroRequest;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Evidencia de que los recursos privados rechazan tokens ausentes,
 * invalidos y vencidos, y aceptan uno bueno (US-21).
 * Cada test usa su propia IP para no chocar con el rate-limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @Value("${JWT_SECRET:petly-dev-secret-local-de-32-caracteres-minimo}")
    private String secreto;

    private final ObjectMapper json = new ObjectMapper();

    private MockHttpServletRequestBuilder desde(String ip, MockHttpServletRequestBuilder peticion) {
        return peticion.with(req -> {
            req.setRemoteAddr(ip);
            return req;
        });
    }

    private String registrarYLogin(String ip, String correo) throws Exception {
        RegistroRequest registro = new RegistroRequest();
        registro.setUsername(correo.split("@")[0]);
        registro.setCorreo(correo);
        registro.setPassword("clave12345");
        mvc.perform(desde(ip, post("/api/auth/registro"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(registro)))
                .andExpect(status().isCreated());

        LoginRequest login = new LoginRequest();
        login.setCorreo(correo);
        login.setPassword("clave12345");
        String cuerpo = mvc.perform(desde(ip, post("/api/auth/login"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode nodo = json.readTree(cuerpo);
        return nodo.get("token").asText();
    }

    @Test
    void recursoPrivadoSinToken() throws Exception {
        mvc.perform(desde("10.0.1.1", get("/api/auth/me")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("no autorizado"));
    }

    @Test
    void recursoPrivadoConTokenBasura() throws Exception {
        mvc.perform(desde("10.0.1.2", get("/api/auth/me"))
                .header("Authorization", "Bearer cadenarota123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token invalido o vencido"));
    }

    @Test
    void recursoPrivadoConTokenVencido() throws Exception {
        var clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        String vencido = Jwts.builder()
                .subject("1")
                .claim("email", "vencido@petly.test")
                .claim("rol", "ADOPTANTE")
                .issuer("petly-spring")
                .audience().add("petly-django").and()
                .issuedAt(new Date(System.currentTimeMillis() - 120_000L))
                .expiration(new Date(System.currentTimeMillis() - 60_000L))
                .signWith(clave)
                .compact();

        mvc.perform(desde("10.0.1.3", get("/api/auth/me"))
                .header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("token invalido o vencido"));
    }

    @Test
    void recursoPrivadoConTokenBueno() throws Exception {
        String token = registrarYLogin("10.0.1.4", "con-token@petly.test");

        mvc.perform(desde("10.0.1.4", get("/api/auth/me"))
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("con-token@petly.test"))
                .andExpect(jsonPath("$.rol").value("ADOPTANTE"));
    }

    @Test
    void loginSiguePublico() throws Exception {
        registrarYLogin("10.0.1.5", "publico@petly.test");
    }
}
