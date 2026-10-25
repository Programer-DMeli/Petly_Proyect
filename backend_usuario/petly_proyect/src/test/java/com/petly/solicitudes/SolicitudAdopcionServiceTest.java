package com.petly.solicitudes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class SolicitudAdopcionServiceTest {
    @Test
    void obtieneHistorialDjangoYReenviaElTokenDelAdoptante() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        SolicitudAdopcionService service = new SolicitudAdopcionService(
                restTemplate, "http://localhost:8000/");
        String historial = "{\"solicitudId\":17,\"estadoActual\":\"PENDIENTE\",\"eventos\":[]}";

        server.expect(requestTo(
                        "http://localhost:8000/api/acceso/solicitudes/17/historial-seguimiento/"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-del-adoptante"))
                .andRespond(withSuccess(historial, MediaType.APPLICATION_JSON));

        var response = service.obtenerSeguimiento(17L, "Bearer token-del-adoptante");

        assertEquals(200, response.getStatusCode().value());
        assertEquals(historial, response.getBody());
        server.verify();
    }
}
