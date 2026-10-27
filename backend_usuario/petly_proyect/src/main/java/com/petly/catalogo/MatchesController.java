package com.petly.catalogo;
import io.jsonwebtoken.Claims;
import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/matches/mascota")
public class MatchesController {
    private final CompatibilidadService scores;
    private final GeminiConsejosService gemini;
    public MatchesController(CompatibilidadService scores, GeminiConsejosService gemini) {
        this.scores = scores; this.gemini = gemini;
    }
    @GetMapping("/{id}")
    public Map<String, Object> detalle(@PathVariable long id, Authentication auth) {
        return scores.detalle(Long.parseLong(((Claims) auth.getPrincipal()).getSubject()), id);
    }
    @PostMapping("/{id}/explicacion")
    public Map<String, Object> explicar(@PathVariable long id, Authentication auth) {
        return gemini.explicar(detalle(id, auth));
    }
}
