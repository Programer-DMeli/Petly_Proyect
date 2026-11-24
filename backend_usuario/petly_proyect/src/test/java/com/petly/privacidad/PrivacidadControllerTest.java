package com.petly.privacidad;

import java.util.Map;
import java.util.UUID;

import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrivacidadControllerTest {
    @Test
    void distingueUnaBajaCompletadaDeUnaPendiente() {
        var servicio = mock(PrivacidadService.class);
        var controlador = new PrivacidadController(servicio);
        var identidad = Jwts.claims().subject("41").build();
        var autenticacion = new UsernamePasswordAuthenticationToken(identidad, null);
        var completada = UUID.randomUUID();
        var pendiente = UUID.randomUUID();

        when(servicio.solicitarBaja(41L, completada))
                .thenReturn(Map.of("estado", SolicitudPrivacidad.Estado.COMPLETADA));
        when(servicio.solicitarBaja(41L, pendiente))
                .thenReturn(Map.of("estado", SolicitudPrivacidad.Estado.PENDIENTE_ARCHIVOS));

        assertEquals(HttpStatus.OK, controlador.solicitarBaja(
                new PrivacidadController.SolicitarBajaRequest(completada, true), autenticacion).getStatusCode());
        assertEquals(HttpStatus.ACCEPTED, controlador.solicitarBaja(
                new PrivacidadController.SolicitarBajaRequest(pendiente, true), autenticacion).getStatusCode());
    }
}
