package com.petly.asistente;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/** Consulta respuestas públicas configuradas por albergues antes de llamar a Gemini (US-33). */
@Service
public class RespuestaAutomaticaChatbotClient {
    private static final Logger log = LoggerFactory.getLogger(RespuestaAutomaticaChatbotClient.class);

    private final RestTemplate django;
    private final String djangoBaseUrl;
    private final ObjectMapper json = new ObjectMapper();

    public RespuestaAutomaticaChatbotClient(
            @Value("${petly.django-api-base-url:http://localhost:8000}") String djangoBaseUrl) {
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1200);
        factory.setReadTimeout(1800);
        this.django = new RestTemplate(factory);
    }

    public Optional<String> buscar(String pregunta, String authorization) {
        try {
            URI uri = URI.create(djangoBaseUrl + "/api/albergues/respuestas-chatbot/coincidencia/");
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
            byte[] body = json.writeValueAsBytes(Map.of("pregunta", pregunta));
            headers.setContentLength(body.length);
            ResponseEntity<Map> response = django.exchange(
                    uri, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
            if (response.getBody() == null) return Optional.empty();
            Object answer = response.getBody().get("respuesta");
            Object shelter = response.getBody().get("albergue");
            if (answer instanceof String text && !text.isBlank()
                    && shelter instanceof String name && !name.isBlank()) {
                return Optional.of(name + ": " + text);
            }
        } catch (RestClientException ex) {
            log.warn("Respuestas automáticas no disponibles: tipo={}", ex.getClass().getSimpleName());
        } catch (JsonProcessingException ex) {
            log.warn("No se pudo preparar la consulta de respuestas automáticas: tipo={}", ex.getClass().getSimpleName());
        }
        return Optional.empty();
    }
}
