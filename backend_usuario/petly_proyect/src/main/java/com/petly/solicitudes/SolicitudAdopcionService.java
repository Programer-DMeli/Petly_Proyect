package com.petly.solicitudes;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.petly.common.exception.ApiException;
import com.petly.solicitudes.dto.CrearSolicitudAdopcionRequest;

/** Coordina el envío de la solicitud a Django, dueño de su persistencia (US-06). */
@Service
public class SolicitudAdopcionService {

    private static final String RUTA_DJANGO = "/api/acceso/solicitudes/";

    private final RestTemplate restTemplate;
    private final String djangoApiBaseUrl;

    public SolicitudAdopcionService(
            RestTemplate restTemplate,
            @Value("${petly.django-api-base-url}") String djangoApiBaseUrl) {
        this.restTemplate = restTemplate;
        this.djangoApiBaseUrl = djangoApiBaseUrl.replaceAll("/+$", "");
    }

    public ResponseEntity<String> crear(CrearSolicitudAdopcionRequest request, String authorization) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mascota", request.mascotaId());
        payload.put("mensaje_postulante", request.mensajePostulante() == null ? "" : request.mensajePostulante());
        payload.put("respuestas_cuestionario",
                request.respuestasCuestionario() == null ? Map.of() : request.respuestasCuestionario());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    djangoApiBaseUrl + RUTA_DJANGO,
                    new HttpEntity<>(payload, headers),
                    String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de solicitudes no está disponible.");
        }
    }

    public ResponseEntity<String> obtenerSeguimiento(Long solicitudId, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + "/api/acceso/solicitudes/{solicitudId}/seguimiento/",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
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
            throw new ApiException(503, "El servicio de seguimiento no está disponible.");
        }
    }

    public ResponseEntity<String> cancelar(Long solicitudId, String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    djangoApiBaseUrl + RUTA_DJANGO + "{solicitudId}/cancelar/",
                    new HttpEntity<>(headers),
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
            throw new ApiException(503, "El servicio de solicitudes no está disponible.");
        }
    }
}
