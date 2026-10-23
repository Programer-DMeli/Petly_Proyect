package com.petly.catalogo;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import io.jsonwebtoken.Claims;
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {
 private final CatalogoService service;
 private final CompatibilidadService compatibilidad;
 public CatalogoController(CatalogoService service, CompatibilidadService compatibilidad){this.service=service;this.compatibilidad=compatibilidad;}
 @GetMapping("/opciones") public Map<String,Object> options(){return service.options();}
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String search,
 @RequestParam(required=false) String especie,@RequestParam(required=false) String tamano,
 @RequestParam(required=false) String temperamento,@RequestParam(required=false) Integer edadMinimaMeses,
 @RequestParam(required=false) Integer edadMaximaMeses,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize, Authentication auth){
 if(auth != null && auth.getPrincipal() instanceof Claims claims && "ADOPTANTE".equals(claims.get("rol", String.class))) {
  return compatibilidad.listar(Long.parseLong(claims.getSubject()),search,especie,tamano,temperamento,edadMinimaMeses,edadMaximaMeses,page,pageSize);
 }
 return service.search(search,especie,tamano,temperamento,edadMinimaMeses,edadMaximaMeses,page,pageSize);}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id, Authentication auth){
  if(auth != null && auth.getPrincipal() instanceof Claims claims && "ADOPTANTE".equals(claims.get("rol", String.class)))
   return compatibilidad.detalle(Long.parseLong(claims.getSubject()), id);
  return service.detail(id);
 }
 @GetMapping("/albergues") public List<Map<String,Object>> shelters(){return service.shelters();}
}
