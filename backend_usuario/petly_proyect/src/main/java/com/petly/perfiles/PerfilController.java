package com.petly.perfiles;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import io.jsonwebtoken.Claims;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import com.petly.common.exception.ApiException;
@RestController
public class PerfilController {
 private final PerfilService perfiles;private final JdbcTemplate db;
 public PerfilController(PerfilService perfiles,JdbcTemplate db){this.perfiles=perfiles;this.db=db;}
 static long id(Authentication a) {return Long.parseLong(((Claims)a.getPrincipal()).getSubject());}
 @GetMapping("/api/perfil") public Map<String,Object> get(Authentication a){return perfiles.get(id(a));}
 @PatchMapping("/api/perfil") public Map<String,Object> save(Authentication a,@RequestBody Map<String,Object> data){return perfiles.save(id(a),data);}
 @GetMapping("/api/autorizaciones") public List<Map<String,Object>> list(Authentication a){return db.query("SELECT ap.albergue_id,a.nombre FROM autorizaciones_perfil ap JOIN albergues_albergue a ON a.id=ap.albergue_id WHERE ap.usuario_id=? ORDER BY a.nombre,a.id",(row,n)->Map.<String,Object>of("albergueId",row.getLong("albergue_id"),"nombre",row.getString("nombre")),id(a));}
 @PutMapping("/api/autorizaciones/{albergueId}") @Transactional
 public Map<String,Object> allow(Authentication a,@PathVariable long albergueId){
  long uid=id(a);db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,uid);
  Long count=db.queryForObject("SELECT COUNT(*) FROM albergues_albergue WHERE id=? AND activo=TRUE",Long.class,albergueId);
  if(count!=1) throw new ApiException(404,"albergue no encontrado");
  if(db.queryForObject("SELECT COUNT(*) FROM autorizaciones_perfil WHERE usuario_id=? AND albergue_id=?",Long.class,uid,albergueId)==0)
   db.update("INSERT INTO autorizaciones_perfil (usuario_id,albergue_id) VALUES (?,?)",uid,albergueId);
  return Map.of("albergueId",albergueId,"autorizado",true);
 }
 @DeleteMapping("/api/autorizaciones/{albergueId}") public Map<String,Object> revoke(Authentication a,@PathVariable long albergueId){
  db.update("DELETE FROM autorizaciones_perfil WHERE usuario_id=? AND albergue_id=?",id(a),albergueId);return Map.of("albergueId",albergueId,"autorizado",false);
 }
 @GetMapping("/api/albergue/perfiles")
 public List<Map<String,Object>> sharedList(Authentication a){
  return db.query("SELECT u.id,u.correo FROM usuarios u JOIN autorizaciones_perfil ap ON ap.usuario_id=u.id JOIN albergues_albergue al ON al.id=ap.albergue_id JOIN acceso_user du ON du.id=al.usuario_id WHERE du.spring_id=? AND al.activo=TRUE ORDER BY u.id",(row,n)->Map.<String,Object>of("adoptanteId",row.getLong("id"),"correo",row.getString("correo")),id(a));
 }
 @GetMapping("/api/albergue/perfiles/{adoptanteId}")
 public Map<String,Object> shared(Authentication a,@PathVariable long adoptanteId){
  var rows=db.queryForList("SELECT al.id FROM albergues_albergue al JOIN acceso_user du ON du.id=al.usuario_id JOIN autorizaciones_perfil ap ON ap.albergue_id=al.id WHERE du.spring_id=? AND al.activo=TRUE AND ap.usuario_id=?",id(a),adoptanteId);
  if(rows.isEmpty()) throw new ApiException(403,"perfil no autorizado");return perfiles.get(adoptanteId);
 }
}
