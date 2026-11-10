package com.petly.solicitudes;

import com.petly.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AsesoriaSeguimientoServiceTest {
    private final RestTemplate http = new RestTemplate();
    private final MockRestServiceServer django = MockRestServiceServer.bindTo(http).build();
    private final SeguimientosAdoptanteService service = new SeguimientosAdoptanteService(http, "http://django.test/");

    @Test void forwardsAuthenticatedRequestAndReturnsPersistedPendingAdvice() {
        String response = "{\"id\":9,\"seguimiento\":12,\"problema\":\"Llora al estar sola\",\"estado\":\"PENDIENTE\"}";
        django.expect(requestTo("http://django.test/api/contratos/asesorias-veterinarias/"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("Authorization", "Bearer adopter"))
                .andExpect(content().json("{\"seguimiento\":12,\"problema\":\"Llora al estar sola\"}"))
                .andExpect(request -> assertTrue(request.getHeaders().getContentLength() > 0))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON).body(response));
        var saved = service.solicitarAsesoria("Bearer adopter",
                new SeguimientosAdoptanteController.SolicitarAsesoriaRequest(12, "  Llora al estar sola  "));
        assertEquals(201, saved.getStatusCode().value());
        assertEquals(response, saved.getBody());
        django.verify();
    }

    @Test void preservesOwnershipAndValidationErrorsWithoutFakingSuccess() {
        for (HttpStatus status : new HttpStatus[]{HttpStatus.BAD_REQUEST, HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND}) {
            django.expect(anything()).andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON)
                    .body("{\"detail\":\"Seguimiento no autorizado\"}"));
        }
        for (int code : new int[]{400, 403, 404}) {
            var response = service.solicitarAsesoria("Bearer adopter",
                    new SeguimientosAdoptanteController.SolicitarAsesoriaRequest(12, "No come"));
            assertEquals(code, response.getStatusCode().value());
            assertTrue(response.getBody().contains("no autorizado"));
        }
        django.verify();
    }

    @Test void unavailableShelterReturns503AndInvalidRequestsNeverReachDjango() {
        for (String motivo : new String[]{" ", "x".repeat(2001)}) {
            assertEquals(400, assertThrows(ApiException.class, () -> service.solicitarAsesoria("Bearer adopter",
                    new SeguimientosAdoptanteController.SolicitarAsesoriaRequest(12, motivo))).status);
        }
        assertEquals(400, assertThrows(ApiException.class, () -> service.solicitarAsesoria("Bearer adopter",
                new SeguimientosAdoptanteController.SolicitarAsesoriaRequest(0, "No come"))).status);
        django.expect(anything()).andRespond(withException(new java.net.SocketTimeoutException()));
        assertEquals(503, assertThrows(ApiException.class, () -> service.solicitarAsesoria("Bearer adopter",
                new SeguimientosAdoptanteController.SolicitarAsesoriaRequest(12, "No come"))).status);
        django.verify();
    }

    @Test void preservesFollowUpPagination() {
        django.expect(requestTo("http://django.test/api/contratos/seguimientos/?page=2"))
                .andExpect(header("Authorization", "Bearer adopter"))
                .andRespond(withSuccess("{\"results\":[],\"next\":null}", MediaType.APPLICATION_JSON));
        assertEquals(200, service.listarSeguimientos("Bearer adopter", 2).getStatusCode().value());
        assertEquals(400, assertThrows(ApiException.class, () -> service.listarSeguimientos("Bearer adopter", 0)).status);
        django.verify();
    }
}
