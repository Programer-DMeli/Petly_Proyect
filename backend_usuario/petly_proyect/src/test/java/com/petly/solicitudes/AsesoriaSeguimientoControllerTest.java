package com.petly.solicitudes;

import com.petly.auth.JwtService;
import com.petly.usuarios.Usuario;
import com.petly.usuarios.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AsesoriaSeguimientoControllerTest {
    @Autowired MockMvc mvc;
    @Autowired UsuarioRepository users;
    @Autowired JwtService jwt;
    @MockitoBean SeguimientosAdoptanteService service;
    private static final String URL = "/api/seguimientos/adoptante/asesorias";

    private String token(String name, Usuario.Rol role) {
        return jwt.generarToken(users.save(new Usuario(name, name + "@example.test", "unused-test-password", role)));
    }

    @Test void onlyAuthenticatedAdoptersCanRequestAdvice() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                .content("{\"seguimiento\":12,\"problema\":\"No come\"}"))
                .andExpect(status().isUnauthorized());
        for (var role : new Usuario.Rol[]{Usuario.Rol.ALBERGUE, Usuario.Rol.ADMIN}) {
            mvc.perform(post(URL).header("Authorization", "Bearer " + token("advice-" + role, role))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"seguimiento\":12,\"problema\":\"No come\"}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void rejectsInvalidRequestsBeforeForwardingAndReturnsCreatedAdvice() throws Exception {
        String authorization = "Bearer " + token("advice-adopter", Usuario.Rol.ADOPTANTE);
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (Object body : new Object[]{java.util.Map.of("seguimiento", 0, "problema", "No come"),
                java.util.Map.of("seguimiento", 12, "problema", "  "),
                java.util.Map.of("seguimiento", 12, "problema", "x".repeat(2001)),
                java.util.Map.of("seguimiento", 12)}) {
            mvc.perform(post(URL).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
        when(service.solicitarAsesoria(eq(authorization), any())).thenReturn(ResponseEntity.status(201)
                .contentType(MediaType.APPLICATION_JSON).body("{\"id\":9,\"seguimiento\":12,\"estado\":\"PENDIENTE\"}"));
        mvc.perform(post(URL).header("Authorization", authorization).contentType(MediaType.APPLICATION_JSON)
                .content("{\"seguimiento\":12,\"problema\":\"No come\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        verify(service).solicitarAsesoria(eq(authorization), argThat(request ->
                request.seguimiento() == 12 && request.problema().equals("No come")));
    }
}
