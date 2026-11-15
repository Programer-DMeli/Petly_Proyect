package com.petly.resenas;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.petly.common.exception.ApiException;

import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/albergue/resenas")
public class ResenaGestionController {

    private final ResenaGestionService service;

    public ResenaGestionController(ResenaGestionService service) {
        this.service = service;
    }

    @GetMapping
    public List<ResenaGestionItem> listar(Authentication authentication) {
        return service.listar(usuarioId(authentication));
    }

    @PutMapping("/{resenaId}/respuesta")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void responder(@PathVariable long resenaId, @Valid @RequestBody ResponderResenaRequest request,
                          Authentication authentication) {
        service.responder(usuarioId(authentication), resenaId, request.respuesta());
    }

    @PatchMapping("/{resenaId}/moderacion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moderar(@PathVariable long resenaId, @Valid @RequestBody ModeracionResenaRequest request,
                        Authentication authentication) {
        service.moderar(usuarioId(authentication), resenaId, request.visible());
    }

    private long usuarioId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Claims claims)) {
            throw new ApiException(401, "La sesión no contiene una identidad válida.");
        }
        try {
            return Long.parseLong(claims.getSubject());
        } catch (NumberFormatException ex) {
            throw new ApiException(401, "La sesión no contiene una identidad válida.");
        }
    }
}
