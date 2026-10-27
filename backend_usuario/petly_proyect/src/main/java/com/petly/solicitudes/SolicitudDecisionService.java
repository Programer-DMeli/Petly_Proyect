package com.petly.solicitudes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.petly.common.exception.ApiException;
import com.petly.solicitudes.dto.DecisionSolicitudRequest;

/** Reenvía a Django las decisiones, que son dueñas de las transiciones (US-07). */
@Service
public class SolicitudDecisionService {

    private final RestTemplate restTemplate;
    private final String djangoApiBaseUrl;

    public SolicitudDecisionService(
            RestTemplate restTemplate,
            @Value("${petly.django-api-base-url}") String djangoApiBaseUrl) {
        this.restTemplate = restTemplate;
        this.djangoApiBaseUrl = djangoApiBaseUrl.replaceAll("/+$", "");
    }

    public ResponseEntity<String> decidir(
            Long solicitudId, DecisionSolicitudRequest request, String authorization) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("estado", request.estado());
        payload.put("observaciones_revision",
                request.observacionesRevision() == null ? "" : request.observacionesRevision());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        byte[] jsonBody;
        try { jsonBody = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(payload); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new ApiException(400, "No se pudo serializar la solicitud."); }
        // El servidor WSGI de desarrollo necesita Content-Length, no transferencia chunked.
        headers.setContentLength(jsonBody.length);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    djangoApiBaseUrl + "/api/acceso/solicitudes/{solicitudId}/cambiar-estado/",
                    new HttpEntity<>(jsonBody, headers),
                    String.class,
                    solicitudId);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de decisiones de solicitudes no está disponible.");
        }
    }
}
