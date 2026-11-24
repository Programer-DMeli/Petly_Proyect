package com.petly.resenas;

import java.math.RoundingMode;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.petly.common.exception.ApiException;

@Service
public class ResenaService {

    private static final String RUTA_ELEGIBILIDAD = "/api/contratos/resenas/eligibilidad/";

    private final ResenaRepository resenas;
    private final RestTemplate http;
    private final String djangoBaseUrl;
    private final String secretoInterno;

    public ResenaService(
            ResenaRepository resenas,
            RestTemplate http,
            @Value("${petly.django-api-base-url}") String djangoBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.resenas = resenas;
        this.http = http;
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    @Transactional(readOnly = true)
    public ResumenResenasResponse obtener(long albergueId) {
        validarAlbergue(albergueId);
        return resumen(albergueId);
    }

    public ResumenResenasResponse crear(long adoptanteId, long albergueId, CrearResenaRequest request) {
        validarAlbergue(albergueId);
        if (resenas.calificacionPropia(adoptanteId, albergueId) != null) {
            throw new ApiException(409, "Ya registraste una reseña para este albergue.");
        }
        validarInteraccion(adoptanteId, albergueId);
        try {
            resenas.crear(adoptanteId, albergueId, request);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(409, "Ya registraste una reseña para este albergue.");
        }
        return resumen(albergueId);
    }

    public EstadoResena estadoPropio(long adoptanteId, long albergueId) {
        validarAlbergue(albergueId);
        Integer calificacion = resenas.calificacionPropia(adoptanteId, albergueId);
        return new EstadoResena(calificacion != null, calificacion);
    }

    public record EstadoResena(boolean registrada, Integer calificacion) {}

    private ResumenResenasResponse resumen(long albergueId) {
        var promedio = resenas.promedio(albergueId).setScale(2, RoundingMode.HALF_UP);
        return new ResumenResenasResponse(albergueId, promedio, resenas.contar(albergueId), resenas.listar(albergueId));
    }

    private void validarAlbergue(long albergueId) {
        if (albergueId <= 0 || !resenas.existeAlbergue(albergueId)) {
            throw new ApiException(404, "No se encontró el albergue.");
        }
    }

    private void validarInteraccion(long adoptanteId, long albergueId) {
        String url = UriComponentsBuilder.fromUriString(djangoBaseUrl + RUTA_ELEGIBILIDAD)
                .queryParam("adoptante_id", adoptanteId)
                .queryParam("albergue_id", albergueId)
                .build()
                .toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Secret", secretoInterno);

        try {
            var response = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), ElegibilidadResenaResponse.class);
            if (response.getBody() == null || !response.getBody().elegible()) {
                throw new ApiException(403, "El albergue debe validar el cierre de tu adopción antes de que puedas reseñarlo.");
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 403 || ex.getStatusCode().value() == 404) {
                throw new ApiException(403, "El albergue debe validar el cierre de tu adopción antes de que puedas reseñarlo.");
            }
            if (ex.getStatusCode().is4xxClientError()) {
                throw new ApiException(400, "No se pudo validar la interacción con el albergue.");
            }
            throw new ApiException(503, "No se pudo validar la interacción con el albergue.");
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de validación de adopciones no está disponible.");
        }
    }

}
