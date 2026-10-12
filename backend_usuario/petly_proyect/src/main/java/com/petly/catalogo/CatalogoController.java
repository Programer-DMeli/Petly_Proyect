package com.petly.catalogo;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/catalogo")
public class CatalogoController {
 private final CatalogoService service;
 public CatalogoController(CatalogoService service){this.service=service;}
 @GetMapping("/opciones") public Map<String,Object> options(){return service.options();}
 @GetMapping public Map<String,Object> list(@RequestParam(defaultValue="") String search,
 @RequestParam(required=false) String especie,@RequestParam(required=false) String tamano,
 @RequestParam(required=false) String temperamento,@RequestParam(required=false) Integer edadMinimaMeses,
 @RequestParam(required=false) Integer edadMaximaMeses,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize){
 return service.search(search,especie,tamano,temperamento,edadMinimaMeses,edadMaximaMeses,page,pageSize);}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id){return service.detail(id);}
 @GetMapping("/albergues") public List<Map<String,Object>> shelters(){return service.shelters();}
}
