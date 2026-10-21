package com.petly.solicitudes;

import com.petly.catalogo.CatalogoService;
import com.petly.common.exception.ApiException;
import com.petly.perfiles.PerfilService;
import io.jsonwebtoken.Claims;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Spring validates the adoptant identity and proxies persistence to Django. */
@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudController {
    private final RestTemplate http;
    private final PerfilService perfiles;
    private final CatalogoService catalogo;
    private final String django;

    public SolicitudController(RestTemplate http, PerfilService perfiles, CatalogoService catalogo,
            @Value("${petly.django-api-base-url:http://localhost:8000}") String django) {
        this.http = http; this.perfiles = perfiles; this.catalogo = catalogo;
        this.django = django.replaceAll("/+$", "");
    }

    @PostMapping
    public ResponseEntity<String> crear(Authentication auth, @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
            @RequestBody Map<String, Object> body) {
        Claims claims = claims(auth);
        long id = id(claims);
        Map<String, Object> profile = perfiles.get(id);
        if (!Boolean.TRUE.equals(profile.get("finalizado"))) throw new ApiException(400, "Completa tu perfil antes de enviar una solicitud.");
        Object raw = body.get("mascota");
        if (!(raw instanceof Number)) throw new ApiException(400, "Indica la mascota que deseas adoptar.");
        Map<String, Object> pet = catalogo.detail(((Number) raw).longValue());
        if (!"Disponible".equals(pet.get("estado"))) throw new ApiException(409, "La mascota ya no está disponible.");
        if (!authorized(id, ((Number) pet.get("albergueId")).longValue())) throw new ApiException(403, "Autoriza a este albergue antes de enviar la solicitud.");
        return proxy(HttpMethod.POST, "/api/acceso/solicitudes/", bearer, body);
    }

    @GetMapping
    public ResponseEntity<String> misSolicitudes(Authentication auth, @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer) {
        claims(auth);
        return proxy(HttpMethod.GET, "/api/acceso/solicitudes/", bearer, null);
    }

    @GetMapping("/{id}/seguimiento")
    public ResponseEntity<String> seguimiento(Authentication auth, @RequestHeader(HttpHeaders.AUTHORIZATION) String bearer,
            @PathVariable long id) {
        claims(auth);
        if (id < 1) throw new ApiException(404, "solicitud no encontrada");
        return proxy(HttpMethod.GET, "/api/acceso/solicitudes/" + id + "/seguimiento/", bearer, null);
    }

    private boolean authorized(long userId, long shelterId) {
        try {
            return perfiles.get(userId) != null && hasAuthorization(userId, shelterId);
        } catch (Exception e) { return false; }
    }

    private boolean hasAuthorization(long userId, long shelterId) {
        // The profile service shares the same database; consent is checked here and again by Django.
        return perfiles.authorizationExists(userId, shelterId);
    }

    private ResponseEntity<String> proxy(HttpMethod method, String path, String bearer, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.set(HttpHeaders.AUTHORIZATION, bearer);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            return http.exchange(django + path, method, new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(e.getResponseBodyAsString());
        } catch (ResourceAccessException e) { throw new ApiException(503, "El servicio de solicitudes no está disponible."); }
    }

    private Claims claims(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof Claims c)) throw new ApiException(401, "no autorizado");
        if (!"ADOPTANTE".equals(c.get("rol", String.class))) throw new ApiException(403, "solo el adoptante puede realizar esta operación");
        return c;
    }
    private long id(Claims claims) { return Long.parseLong(claims.getSubject()); }
}
