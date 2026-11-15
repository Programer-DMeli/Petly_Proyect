package com.petly.comunidad;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/casos-exito/{casoId}/interacciones")
public class InteraccionesCasoExitoController {

    private final InteraccionesCasoExitoService service;

    public InteraccionesCasoExitoController(InteraccionesCasoExitoService service) {
        this.service = service;
    }

    @GetMapping
    public InteraccionesCasoExitoResponse obtener(@PathVariable long casoId, Authentication authentication) {
        Long usuarioId = authentication != null && authentication.getPrincipal() instanceof Claims claims
                ? Long.parseLong(claims.getSubject())
                : null;
        return service.obtener(casoId, usuarioId);
    }

    @PutMapping("/me-gusta")
    public InteraccionesCasoExitoResponse agregarMeGusta(@PathVariable long casoId, Authentication authentication) {
        return service.agregarMeGusta(casoId, usuarioId(authentication));
    }

    @DeleteMapping("/me-gusta")
    public InteraccionesCasoExitoResponse quitarMeGusta(@PathVariable long casoId, Authentication authentication) {
        return service.quitarMeGusta(casoId, usuarioId(authentication));
    }

    @PostMapping("/comentarios")
    @ResponseStatus(HttpStatus.CREATED)
    public InteraccionesCasoExitoResponse comentar(
            @PathVariable long casoId,
            @Valid @RequestBody ComentarioCasoExitoRequest request,
            Authentication authentication) {
        return service.comentar(casoId, usuarioId(authentication), request);
    }

    private static long usuarioId(Authentication authentication) {
        return Long.parseLong(((Claims) authentication.getPrincipal()).getSubject());
    }
}
