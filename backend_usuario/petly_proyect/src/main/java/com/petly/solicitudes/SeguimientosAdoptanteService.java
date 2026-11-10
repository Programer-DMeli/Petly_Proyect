package com.petly.solicitudes;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import com.petly.common.exception.ApiException;

/** Consulta en Django los seguimientos visibles para el adoptante autenticado. */
@Service
public class SeguimientosAdoptanteService {

    private static final String RUTA_DJANGO = "/api/contratos/seguimientos/";

    private final RestTemplate restTemplate;
    private final String djangoApiBaseUrl;

    public SeguimientosAdoptanteService(
            RestTemplate restTemplate,
            @Value("${petly.django-api-base-url}") String djangoApiBaseUrl) {
        this.restTemplate = restTemplate;
        this.djangoApiBaseUrl = djangoApiBaseUrl.replaceAll("/+$", "");
    }

    public ResponseEntity<String> listarSeguimientos(String authorization, int page) {
        if (page < 1 || page > 100000) throw new ApiException(400, "Página inválida.");
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + RUTA_DJANGO + "?page=" + page,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de seguimientos no está disponible.");
        }
    }

    public ResponseEntity<String> solicitarAsesoria(String authorization,
            SeguimientosAdoptanteController.SolicitarAsesoriaRequest solicitud) {
        String motivo = solicitud.problema() == null ? "" : solicitud.problema().strip();
        if (solicitud.seguimiento() <= 0 || motivo.isBlank() || motivo.length() > 2000)
            throw new ApiException(400, "Selecciona un seguimiento y describe el motivo con hasta 2000 caracteres.");
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.AUTHORIZATION, authorization);
        byte[] payload;
        try {
            payload = new ObjectMapper().writeValueAsBytes(Map.of(
                    "seguimiento", solicitud.seguimiento(), "problema", motivo));
        } catch (JsonProcessingException ex) {
            throw new ApiException(400, "No se pudo preparar la solicitud de asesoría.");
        }
        headers.setContentLength(payload.length);
        try {
            // Django checks ownership and derives the shelter from the follow-up; clients cannot choose it.
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + "/api/contratos/asesorias-veterinarias/", HttpMethod.POST,
                    new HttpEntity<>(payload, headers), String.class);
            return ResponseEntity.status(response.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "No se pudo contactar con el albergue. Vuelve a intentar.");
        }
    }

    public ResponseEntity<String> enviarReporte(
            String authorization,
            String seguimientoId,
            String estadoBienestar,
            String observaciones,
            List<MultipartFile> fotos) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("seguimiento_id", seguimientoId);
        form.add("estado_bienestar", estadoBienestar);
        form.add("observaciones", observaciones == null ? "" : observaciones);
        try {
            if (fotos != null) {
                for (MultipartFile foto : fotos) {
                    HttpHeaders photoHeaders = new HttpHeaders();
                    // Django valida el formato real de la imagen; no se confía en el MIME del navegador.
                    photoHeaders.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                    ByteArrayResource resource = new ByteArrayResource(foto.getBytes()) {
                        @Override
                        public String getFilename() {
                            return foto.getOriginalFilename() == null ? "foto" : foto.getOriginalFilename();
                        }
                    };
                    form.add("fotos", new HttpEntity<>(resource, photoHeaders));
                }
            }
        } catch (IOException ex) {
            throw new ApiException(400, "No se pudo leer una de las fotos del reporte.");
        }

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + "/api/contratos/reportes-seguimiento/",
                    HttpMethod.POST,
                    new HttpEntity<>(form, headers),
                    String.class);
            return ResponseEntity.status(response.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(response.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de reportes de bienestar no está disponible.");
        }
    }
}
