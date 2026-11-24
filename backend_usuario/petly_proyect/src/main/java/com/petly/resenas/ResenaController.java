package com.petly.resenas;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/albergues/{albergueId}/resenas")
public class ResenaController {

    private final ResenaService service;

    public ResenaController(ResenaService service) {
        this.service = service;
    }

    @GetMapping
    public ResumenResenasResponse listar(@PathVariable long albergueId) {
        return service.obtener(albergueId);
    }

    @GetMapping("/mi-resena")
    public ResponseEntity<ResenaService.EstadoResena> estadoPropio(
            @PathVariable long albergueId, Authentication authentication) {
        long adoptanteId = Long.parseLong(((Claims) authentication.getPrincipal()).getSubject());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.estadoPropio(adoptanteId, albergueId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResumenResenasResponse crear(
            @PathVariable long albergueId,
            @Valid @RequestBody CrearResenaRequest request,
            Authentication authentication) {
        long adoptanteId = Long.parseLong(((Claims) authentication.getPrincipal()).getSubject());
        return service.crear(adoptanteId, albergueId, request);
    }
}
