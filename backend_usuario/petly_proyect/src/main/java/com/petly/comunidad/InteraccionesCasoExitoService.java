package com.petly.comunidad;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.petly.common.exception.ApiException;

@Service
public class InteraccionesCasoExitoService {

    private final InteraccionesCasoExitoRepository interacciones;
    private final RestTemplate http;
    private final String djangoBaseUrl;
    private final String secretoInterno;

    public InteraccionesCasoExitoService(
            InteraccionesCasoExitoRepository interacciones,
            RestTemplate http,
            @Value("${petly.django-api-base-url}") String djangoBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.interacciones = interacciones;
        this.http = http;
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    public InteraccionesCasoExitoResponse obtener(long casoId, Long usuarioId) {
        validarCasoId(casoId);
        validarPublicado(casoId);
        return interacciones.obtener(casoId, usuarioId);
    }

    public InteraccionesCasoExitoResponse agregarMeGusta(long casoId, long usuarioId) {
        validarCasoId(casoId);
        validarPublicado(casoId);
        interacciones.agregarMeGusta(casoId, usuarioId);
        return interacciones.obtener(casoId, usuarioId);
    }

    public InteraccionesCasoExitoResponse quitarMeGusta(long casoId, long usuarioId) {
        validarCasoId(casoId);
        validarPublicado(casoId);
        interacciones.quitarMeGusta(casoId, usuarioId);
        return interacciones.obtener(casoId, usuarioId);
    }

    public InteraccionesCasoExitoResponse comentar(long casoId, long usuarioId, ComentarioCasoExitoRequest request) {
        validarCasoId(casoId);
        validarPublicado(casoId);
        interacciones.agregarComentario(casoId, usuarioId, request.comentario());
        return interacciones.obtener(casoId, usuarioId);
    }

    private static void validarCasoId(long casoId) {
        if (casoId <= 0) {
            throw new ApiException(400, "El identificador de la historia debe ser positivo.");
        }
    }

    private void validarPublicado(long casoId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Secret", secretoInterno);
        String url = djangoBaseUrl + "/api/contratos/casos-exito/" + casoId + "/visible/";

        try {
            var response = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), CasoExitoVisibleResponse.class);
            if (response.getBody() == null || !response.getBody().visible()) {
                throw new ApiException(404, "La historia no está publicada.");
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ApiException(404, "La historia no existe o no está publicada.");
            }
            throw new ApiException(503, "No se pudo validar la publicación de la historia.");
        } catch (ResourceAccessException ex) {
            throw new ApiException(503, "El servicio de historias no está disponible.");
        }
    }
}
