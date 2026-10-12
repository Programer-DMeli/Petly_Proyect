package com.petly.usuarios;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import com.petly.common.exception.ApiException;
@RestController
@RequestMapping("/api/admin/cuentas-albergue")
public class CuentasAlbergueController {
 private final UsuarioRepository users;
 public CuentasAlbergueController(UsuarioRepository users){this.users=users;}
 private Map<String,Object> identity(Usuario u){return Map.of("id",u.getId(),"correo",u.getCorreo(),"rol",u.getRol().name());}
 @GetMapping public List<Map<String,Object>> list(){return users.findByRolOrderByIdAsc(Usuario.Rol.ALBERGUE).stream().map(this::identity).toList();}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable long id){
  Usuario u=users.findById(id).filter(v->v.getRol()==Usuario.Rol.ALBERGUE).orElseThrow(()->new ApiException(404,"cuenta de albergue no encontrada"));return identity(u);
 }
}
