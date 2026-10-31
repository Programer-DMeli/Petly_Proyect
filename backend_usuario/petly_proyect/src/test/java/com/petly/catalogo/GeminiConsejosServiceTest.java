package com.petly.catalogo;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class GeminiConsejosServiceTest {
    Map<String,Object> detail() {
        return CompatibilidadService.withScore(Map.of("especie","Perro","tamano","Grande","energia","Alta","temperamento","Sociable"),
            Map.of("finalizado",true,"vivienda","Casa","horasFuera",3,"tieneNinos",false,"tieneOtrasMascotas",false));
    }
    @Test void disabledDoesNotModifyRules() {
        var original = detail(); var service = new GeminiConsejosService("", "gemini-2.5-flash", 1);
        assertEquals("NO_CONFIGURADO", service.explicar(original).get("estado"));
        assertEquals(100, ((Map<?,?>) original.get("compatibilidad")).get("porcentaje"));
    }
    @Test void validStructuredResponseWhitelistsOnlyAdvice() {
        var service = new GeminiConsejosService("fake", "gemini-2.5-flash", 1);
        var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service,"http")).build();
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"))
            .andExpect(header("x-goog-api-key", "fake"))
            .andRespond(withSuccess("{\"candidates\":[{\"finishReason\":\"STOP\",\"content\":{\"parts\":[{\"text\":\"{\\\"explicacion\\\":\\\"Rutina compatible.\\\",\\\"recomendaciones\\\":[\\\"Planifica paseos.\\\"],\\\"score\\\":1}\"}]}}]}", MediaType.APPLICATION_JSON));
        var original = detail(); var result = service.explicar(original);
        assertEquals("DISPONIBLE", result.get("estado")); assertFalse(result.containsKey("score"));
        assertEquals(100, ((Map<?,?>) original.get("compatibilidad")).get("porcentaje")); server.verify();
    }
    @Test void invalidHttpAndTransportResponsesAreOptional() {
        for (var response : List.of(withServerError(), withSuccess("{}", MediaType.APPLICATION_JSON),
                withSuccess("not-json", MediaType.APPLICATION_JSON), withException(new java.net.SocketTimeoutException()))) {
            var service = new GeminiConsejosService("fake", "gemini-2.5-flash", 1);
            var server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service,"http")).build();
            server.expect(anything()).andRespond(response);
            var original = detail(); assertEquals("NO_DISPONIBLE",service.explicar(original).get("estado"));
            assertEquals(100, ((Map<?,?>) original.get("compatibilidad")).get("porcentaje")); server.verify();
        }
    }
}
