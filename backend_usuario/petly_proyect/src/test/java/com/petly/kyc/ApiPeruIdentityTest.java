package com.petly.kyc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import com.petly.common.exception.ApiException;

class ApiPeruIdentityTest {
    @Test
    void comparaNombresNormalizadosYDetectaDiscrepancias() {
        ApiPeruDniClient api = mock(ApiPeruDniClient.class);
        when(api.consultar("12345678")).thenReturn(
                new ApiPeruDniResult("12345678", "JOSÉ LUIS", "PÉREZ LOPEZ", "ApiPeruDev"));
        ReniecClient client = new ReniecClient("", "", "RENIEC", 5000, api);
        assertTrue(client.consultar("12345678", " José  Luis ", "Perez Lopez").coincide());
        assertFalse(client.consultar("12345678", "Carlos", "Perez Lopez").coincide());
        assertEquals("ApiPeruDev", client.getProvider());
    }

    @Test
    void falloDelProveedorNoGeneraCoincidencia() {
        ApiPeruDniClient api = mock(ApiPeruDniClient.class);
        when(api.consultar("12345678")).thenThrow(new ApiException(503, "No disponible"));
        ReniecClient client = new ReniecClient("", "", "RENIEC", 5000, api);
        assertThrows(ReniecNoDisponibleException.class,
                () -> client.consultar("12345678", "Jose", "Perez"));
    }

    @Test
    void rechazaRespuestaDeOtroDni() {
        ApiPeruDniClient api = new ApiPeruDniClient("https://api.example.test/dni", "test-token", 5000);
        RestTemplate rest = (RestTemplate) ReflectionTestUtils.getField(api, "restTemplate");
        MockRestServiceServer server = MockRestServiceServer.bindTo(rest).build();
        server.expect(requestTo("https://api.example.test/dni"))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("{\"dni\":\"12345678\"}"))
                .andRespond(withSuccess("""
                        {"success":true,"data":{"numero":"87654321","nombres":"Jose",
                        "apellido_paterno":"Perez","apellido_materno":"Lopez"}}
                        """, MediaType.APPLICATION_JSON));
        assertThrows(ApiException.class, () -> api.consultar("12345678"));
        server.verify();
    }
}
