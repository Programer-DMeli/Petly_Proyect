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
import org.springframework.http.HttpStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petly.notificaciones.AlertaCompatibilidadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.petly.common.exception.ApiException;
import com.petly.solicitudes.dto.CrearSolicitudAdopcionRequest;

/** Coordina el envío de la solicitud a Django, dueño de su persistencia (US-06). */
@Service
public class SolicitudAdopcionService {

    private static final Logger log = LoggerFactory.getLogger(SolicitudAdopcionService.class);

    private static final String RUTA_DJANGO = "/api/acceso/solicitudes/";

    private final RestTemplate restTemplate;
    private final String djangoApiBaseUrl;
    private final AlertaCompatibilidadService alertasCompatibilidad;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SolicitudAdopcionService(
            RestTemplate restTemplate,
            @Value("${petly.django-api-base-url}") String djangoApiBaseUrl,
            AlertaCompatibilidadService alertasCompatibilidad) {
        this.restTemplate = restTemplate;
        this.djangoApiBaseUrl = djangoApiBaseUrl.replaceAll("/+$", "");
        this.alertasCompatibilidad = alertasCompatibilidad;
    }

    public ResponseEntity<String> crear(CrearSolicitudAdopcionRequest request, String authorization, long adoptanteId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mascota", request.mascotaId());
        payload.put("mensaje_postulante", request.mensajePostulante() == null ? "" : request.mensajePostulante());
        payload.put("respuestas_cuestionario",
                request.respuestasCuestionario() == null ? Map.of() : request.respuestasCuestionario());

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
                    djangoApiBaseUrl + RUTA_DJANGO,
                    new HttpEntity<>(jsonBody, headers),
                    String.class);
            if (response.getStatusCode() == HttpStatus.CREATED && response.getBody() != null) {
                try {
                    JsonNode body = objectMapper.readTree(response.getBody());
                    if (body.path("id").canConvertToLong()) {
                        alertasCompatibilidad.registrarSiSuperaUmbral(
                                body.path("id").longValue(), adoptanteId, request.mascotaId());
                    }
                } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
                    // La solicitud ya quedó registrada; la alerta nunca debe invalidar esa respuesta.
                    log.warn("No se pudo evaluar la compatibilidad de la solicitud creada.", ex);
                }
            }
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

    public ResponseEntity<String> misSolicitudes(String authorization, int page) {
        if (page < 1 || page > 100000) throw new ApiException(400, "Página inválida.");
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + RUTA_DJANGO + "?page=" + page, HttpMethod.GET, new HttpEntity<>(headers), String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(ex.getResponseBodyAsString());
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
                    djangoApiBaseUrl + "/api/acceso/solicitudes/{solicitudId}/historial-seguimiento/",
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
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + RUTA_DJANGO + "{solicitudId}/cancelar/",
                    HttpMethod.POST, new HttpEntity<>(headers), String.class, solicitudId);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON).body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de solicitudes no está disponible.");
        }
    }
}
