package com.petly.perfiles;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import io.jsonwebtoken.Claims;
import org.springframework.jdbc.core.JdbcTemplate;
import com.petly.common.exception.ApiException;
@RestController
public class PerfilController {
 private final PerfilService perfiles;private final JdbcTemplate db;
 @org.springframework.beans.factory.annotation.Autowired private PreferenciasService preferencias;
 public PerfilController(PerfilService perfiles,JdbcTemplate db){this.perfiles=perfiles;this.db=db;}
 static long id(Authentication a) {return Long.parseLong(((Claims)a.getPrincipal()).getSubject());}
 @GetMapping("/api/perfil") public Map<String,Object> get(Authentication a){return perfiles.get(id(a));}
 @PatchMapping("/api/perfil") public Map<String,Object> save(Authentication a,@RequestBody Map<String,Object> data){return perfiles.save(id(a),data);}
 @GetMapping("/api/albergue/perfiles")
 public List<Map<String,Object>> sharedList(Authentication a){
  return db.query("SELECT u.id,u.correo FROM usuarios u JOIN autorizaciones_consulta ac ON ac.adoptante_id=u.id JOIN albergues_albergue al ON al.id=ac.albergue_id JOIN acceso_user du ON du.id=al.usuario_id WHERE du.spring_id=? AND al.activo=TRUE AND ac.estado='ACTIVA' ORDER BY u.id",(row,n)->Map.<String,Object>of("adoptanteId",row.getLong("id"),"correo",row.getString("correo")),id(a));
 }
 @GetMapping("/api/albergue/perfiles/{adoptanteId}")
 public Map<String,Object> shared(Authentication a,@PathVariable long adoptanteId){
  var rows=db.queryForList("SELECT al.id FROM albergues_albergue al JOIN acceso_user du ON du.id=al.usuario_id JOIN autorizaciones_consulta ac ON ac.albergue_id=al.id WHERE du.spring_id=? AND al.activo=TRUE AND ac.adoptante_id=? AND ac.estado='ACTIVA'",id(a),adoptanteId);
  if(rows.isEmpty()) throw new ApiException(403,"perfil no autorizado");var perfil = perfiles.get(adoptanteId); perfil.put("disponibilidad", preferencias.horarios(adoptanteId)); perfil.put("zonaHoraria", "America/Lima"); return perfil;
 }
}
