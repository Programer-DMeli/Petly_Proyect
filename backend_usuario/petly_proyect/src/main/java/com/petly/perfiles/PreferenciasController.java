package com.petly.perfiles;

import java.util.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfil")
public class PreferenciasController {
    private final PreferenciasService service;
    public PreferenciasController(PreferenciasService service) { this.service=service; }
    @GetMapping("/favoritos") public List<Map<String,Object>> favoritos(Authentication a) { return service.favoritos(PerfilController.id(a)); }
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) @PutMapping("/favoritos/{id}") public void agregar(Authentication a,@PathVariable long id) { service.favorito(PerfilController.id(a),id,true); }
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) @DeleteMapping("/favoritos/{id}") public void quitar(Authentication a,@PathVariable long id) { service.favorito(PerfilController.id(a),id,false); }
    @GetMapping("/disponibilidad") public List<PreferenciasService.Franja> horarios(Authentication a) { return service.horarios(PerfilController.id(a)); }
    @PostMapping("/disponibilidad") public List<PreferenciasService.Franja> guardar(Authentication a,@RequestBody List<PreferenciasService.Franja> rows) { return service.guardar(PerfilController.id(a),rows); }
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT) @DeleteMapping("/disponibilidad/{id}") public void eliminar(Authentication a,@PathVariable long id) { service.eliminar(PerfilController.id(a),id); }
}
