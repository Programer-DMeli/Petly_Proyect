package com.petly.solicitudes;

import java.util.List;

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

    public ResponseEntity<String> listarSeguimientos(String authorization) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(HttpHeaders.AUTHORIZATION, authorization);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    djangoApiBaseUrl + RUTA_DJANGO,
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
}
