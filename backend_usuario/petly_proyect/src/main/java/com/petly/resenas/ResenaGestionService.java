package com.petly.resenas;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
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
public class ResenaGestionService {

    private static final String RUTA_ALBERGUE_PROPIO = "/api/contratos/resenas/albergue-propio/";

    private final ResenaRepository resenas;
    private final RestTemplate http;
    private final String djangoBaseUrl;
    private final String secretoInterno;

    public ResenaGestionService(ResenaRepository resenas, RestTemplate http,
            @Value("${petly.django-api-base-url}") String djangoBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.resenas = resenas;
        this.http = http;
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    @Transactional(readOnly = true)
    public List<ResenaGestionItem> listar(long usuarioSpringId) {
        return resenas.listarParaGestion(alberguePropioId(usuarioSpringId));
    }

    @Transactional
    public void responder(long usuarioSpringId, long resenaId, String respuesta) {
        long albergueId = validarPropiedadResena(usuarioSpringId, resenaId);
        if (!resenas.responder(resenaId, albergueId, respuesta.trim())) {
            throw new ApiException(404, "No se encontró la reseña.");
        }
    }

    @Transactional
    public void moderar(long usuarioSpringId, long resenaId, boolean visible) {
        long albergueId = validarPropiedadResena(usuarioSpringId, resenaId);
        if (!resenas.actualizarVisibilidad(resenaId, albergueId, visible)) {
            throw new ApiException(404, "No se encontró la reseña.");
        }
    }

    private long validarPropiedadResena(long usuarioSpringId, long resenaId) {
        if (resenaId <= 0) {
            throw new ApiException(404, "No se encontró la reseña.");
        }
        Long albergueId = resenas.obtenerAlbergueId(resenaId);
        if (albergueId == null) {
            throw new ApiException(404, "No se encontró la reseña.");
        }
        long propio = alberguePropioId(usuarioSpringId);
        if (propio != albergueId) {
            throw new ApiException(403, "No tienes permiso para gestionar esta reseña.");
        }
        return propio;
    }

    private long alberguePropioId(long usuarioSpringId) {
        String url = UriComponentsBuilder.fromUriString(djangoBaseUrl + RUTA_ALBERGUE_PROPIO)
                .queryParam("usuario_spring_id", usuarioSpringId).build().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Secret", secretoInterno);

        try {
            var response = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), AlberguePropioResponse.class);
            if (response.getBody() == null || response.getBody().albergueId() == null) {
                throw new ApiException(503, "El servicio de albergues devolvió una respuesta inválida.");
            }
            return response.getBody().albergueId();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ApiException(404, "La cuenta no tiene un albergue asociado.");
            }
            throw new ApiException(503, "No se pudo validar la titularidad del albergue.");
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de albergues no está disponible.");
        }
    }
}
