package com.petly.auth;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petly.usuarios.SincronizarRolDjango;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"petly.session.cookie-secure=true"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SesionRenovableTest {
    private static final String ORIGIN = "http://localhost:5173";
    private static final String PASSWORD = "una-clave-de-prueba-123";
    private final ObjectMapper json = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtService jwt;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired RecuperacionTokenRepository recuperaciones;
    @MockitoBean SincronizarRolDjango sincronizadorRol;
    @Autowired SesionRenovableService sesiones;
    @Value("${spring.jwt.secret}") String key;

    private Usuario usuario() {
        String id = UUID.randomUUID().toString();
        return usuarios.saveAndFlush(new Usuario(id, id + "@petly.test", passwords.encode(PASSWORD), Usuario.Rol.ADOPTANTE));
    }

    @Test
    void renuevaAccesoVencidoYRotaConElMismoResultadoAntePerdidaDeRespuesta() throws Exception {
        Usuario usuario = usuario();
        var login = sesiones.iniciar(usuario);
        var vencido = Jwts.builder().issuer("petly-spring").audience().add("petly-django").and()
                .subject(usuario.getId().toString()).claim("type", "access").claim("version", 0)
                .expiration(Date.from(Instant.now().minusSeconds(10)))
                .signWith(Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256).compact();
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + vencido))
                .andExpect(status().isUnauthorized());

        String operation = UUID.randomUUID().toString();
        JsonNode first = refresh(login.renovacion(), operation, 200);
        JsonNode repeated = refresh(login.renovacion(), operation, 200);
        String rotated = first.get("refreshToken").asText();
        assertNotEquals(login.renovacion(), rotated);
        assertEquals(rotated, repeated.get("refreshToken").asText());
        refresh(login.renovacion(), UUID.randomUUID().toString(), 401);
        var claims = jwt.validarToken(first.get("token").asText());
        assertEquals(3600L, (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000);
        assertTrue(claims.getAudience().contains("petly-django"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + first.get("token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void concurrentesConLaMismaOperacionCompartenLaRotacion() throws Exception {
        String token = sesiones.iniciar(usuario()).renovacion();
        String operation = UUID.randomUUID().toString();
        try (var workers = Executors.newFixedThreadPool(2)) {
            var one = workers.submit(() -> sesiones.renovar(token, operation).renovacion());
            var two = workers.submit(() -> sesiones.renovar(token, operation).renovacion());
            assertEquals(one.get(), two.get());
        }
    }

    @Test
    void cookieWebProtegidaYLogoutRevocaElJwtActivo() throws Exception {
        Usuario usuario = usuario();
        var login = mvc.perform(post("/api/auth/login")
                .header("Origin", ORIGIN).header(SesionWeb.CANAL, "web")
                .contentType(APPLICATION_JSON).content(json.writeValueAsBytes(
                        java.util.Map.of("correo", usuario.getCorreo(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("Secure")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andReturn().getResponse();
        String value = login.getHeader("Set-Cookie").split(";", 2)[0].split("=", 2)[1];
        Cookie cookie = new Cookie(SesionWeb.COOKIE, value);
        String access = json.readTree(login.getContentAsByteArray()).get("token").asText();

        mvc.perform(post("/api/auth/refresh").cookie(cookie).header("Origin", ORIGIN)
                .contentType(APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", "https://otro.example")
                .header(SesionWeb.CANAL, "web").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("Origin", ORIGIN)
                .header(SesionWeb.CANAL, "web").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent()).andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        refresh(value, UUID.randomUUID().toString(), 401);
    }

    @Test
    void cambiarPasswordInvalidaTodasLasSesiones() throws Exception {
        Usuario usuario = usuario();
        var primera = sesiones.iniciar(usuario);
        var segunda = sesiones.iniciar(usuario);
        mvc.perform(post("/api/auth/password/change").header("Authorization", "Bearer " + primera.acceso().getToken())
                .contentType(APPLICATION_JSON).content(json.writeValueAsBytes(java.util.Map.of(
                        "passwordActual", PASSWORD, "nuevaPassword", "nueva-123456",
                        "nuevaPasswordConfirm", "nueva-123456")))).andExpect(status().isNoContent());
        refresh(primera.renovacion(), UUID.randomUUID().toString(), 401);
        refresh(segunda.renovacion(), UUID.randomUUID().toString(), 401);
    }

    @Test
    void rechazaUnaRenovacionVencida() throws Exception {
        var login = sesiones.iniciar(usuario());
        jdbc.update("UPDATE sesiones_renovables SET expira_en = ? WHERE id = ?",
                LocalDateTime.now().minusDays(1), login.renovacion().substring(0, 36));
        refresh(login.renovacion(), UUID.randomUUID().toString(), 401);
    }

    @Test
    void losPermisosAdministrativosSiguenElRolAsignado() throws Exception {
        Usuario cuenta = usuario();
        var sesionAnterior = sesiones.iniciar(cuenta);
        String autorizacionAnterior = "Bearer " + sesionAnterior.acceso().getToken();
        String alias = UUID.randomUUID().toString();
        Usuario admin = usuarios.saveAndFlush(
                new Usuario(alias, alias + "@petly.test", passwords.encode(PASSWORD), Usuario.Rol.ADMIN));
        String autorizacionAdmin = "Bearer " + sesiones.iniciar(admin).acceso().getToken();
        String ruta = "/api/admin/usuarios/" + cuenta.getId() + "/rol";
        String nuevoRol = "{\"rol\":\"ADMIN\"}";

        mvc.perform(get("/api/admin/usuarios").header("Authorization", autorizacionAnterior))
                .andExpect(status().isForbidden());
        mvc.perform(patch(ruta).header("Authorization", autorizacionAnterior)
                .contentType(APPLICATION_JSON).content(nuevoRol)).andExpect(status().isForbidden());
        mvc.perform(patch(ruta).header("Authorization", autorizacionAdmin)
                .contentType(APPLICATION_JSON).content(nuevoRol))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rol").value("ADMIN"));
        verify(sincronizadorRol).sincronizar(argThat(usuario -> cuenta.getId().equals(usuario.getId())
                && usuario.getRol() == Usuario.Rol.ADMIN));

        mvc.perform(get("/api/admin/usuarios").header("Authorization", autorizacionAnterior))
                .andExpect(status().isUnauthorized());
        refresh(sesionAnterior.renovacion(), UUID.randomUUID().toString(), 401);
        Usuario actualizado = usuarios.findById(cuenta.getId()).orElseThrow();
        String autorizacionNueva = "Bearer " + sesiones.iniciar(actualizado).acceso().getToken();
        mvc.perform(get("/api/admin/usuarios").header("Authorization", autorizacionNueva))
                .andExpect(status().isOk());
    }

    @Test
    void confirmarRestablecimientoRevocaRenovacionYAcceso() throws Exception {
        Usuario usuario = usuario();
        var login = sesiones.iniciar(usuario);
        String codigo = UUID.randomUUID().toString();
        recuperaciones.saveAndFlush(new RecuperacionToken(usuario.getId(), AuthService.hash(codigo),
                LocalDateTime.now().plusMinutes(10)));
        mvc.perform(post("/api/auth/password/reset/confirm").contentType(APPLICATION_JSON)
                .content(json.writeValueAsBytes(java.util.Map.of("token", codigo,
                        "nuevaPassword", "nueva-123456", "nuevaPasswordConfirm", "nueva-123456"))))
                .andExpect(status().isOk());
        refresh(login.renovacion(), UUID.randomUUID().toString(), 401);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + login.acceso().getToken()))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode refresh(String token, String operation, int statusCode) throws Exception {
        var result = mvc.perform(post("/api/auth/refresh").contentType(APPLICATION_JSON)
                .content(json.writeValueAsBytes(java.util.Map.of("refreshToken", token, "requestId", operation))))
                .andExpect(status().is(statusCode)).andReturn().getResponse();
        return json.readTree(result.getContentAsByteArray());
    }
}
