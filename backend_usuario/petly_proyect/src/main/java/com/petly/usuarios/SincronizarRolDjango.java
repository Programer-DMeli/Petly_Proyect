package com.petly.usuarios;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.petly.common.exception.ApiException;

/** Mantiene el rol de la identidad local de Django alineado con Spring, su autoridad. */
@Component
public class SincronizarRolDjango {

    private static final String RUTA_SINCRONIZAR_ROL = "/api/acceso/interno/roles/";

    private final RestTemplate http;
    private final String djangoBaseUrl;
    private final String secretoInterno;

    public SincronizarRolDjango(
            RestTemplate http,
            @Value("${petly.django-api-base-url}") String djangoBaseUrl,
            @Value("${interno.api-secret}") String secretoInterno) {
        this.http = http;
        this.djangoBaseUrl = djangoBaseUrl.replaceAll("/+$", "");
        this.secretoInterno = secretoInterno;
    }

    public void sincronizar(Usuario usuario) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("X-Internal-Secret", secretoInterno);

        var request = new RolDjangoRequest(usuario.getId(), usuario.getCorreo(), usuario.getRol().name());
        try {
            RolDjangoResponse response = http.exchange(
                    djangoBaseUrl + RUTA_SINCRONIZAR_ROL,
                    HttpMethod.PUT,
                    new HttpEntity<>(request, headers),
                    RolDjangoResponse.class).getBody();
            if (response == null || !response.sincronizado()
                    || response.usuarioSpringId() != usuario.getId()
                    || !usuario.getRol().name().equals(response.rol())) {
                throw new ApiException(503, "El servicio de albergues no confirmó la sincronización del rol.");
            }
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientException ex) {
            throw new ApiException(503, "No se pudo sincronizar el rol con el servicio de albergues.");
        }
    }

    private record RolDjangoRequest(
            @JsonProperty("usuario_spring_id") long usuarioSpringId,
            String correo,
            String rol) {}

    private record RolDjangoResponse(
            @JsonProperty("usuario_spring_id") long usuarioSpringId,
            String rol,
            boolean sincronizado) {}
}
