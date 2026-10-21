package com.petly.perfiles;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.petly.common.exception.ApiException;
@Service
public class PerfilService {
 private final JdbcTemplate db;
 public PerfilService(JdbcTemplate db) {this.db=db;}
 public boolean authorizationExists(long userId,long shelterId) {
  return db.queryForObject("SELECT COUNT(*) FROM autorizaciones_perfil ap JOIN albergues_albergue a ON a.id=ap.albergue_id WHERE ap.usuario_id=? AND ap.albergue_id=? AND a.activo=TRUE",Long.class,userId,shelterId)==1;
 }
 private static final Set<String> FIELDS=Set.of("vivienda","horasFuera","presupuesto","tieneNinos","tieneOtrasMascotas","finalizado");
 public Map<String,Object> get(long id) {
  var rows=db.queryForList("SELECT * FROM perfiles_adoptantes WHERE usuario_id=?",id);
  Map<String,Object> out=new LinkedHashMap<>();
  var row=rows.isEmpty()?Map.<String,Object>of():rows.get(0);
  out.put("vivienda",row.get("vivienda"));out.put("horasFuera",row.get("horas_fuera"));out.put("presupuesto",row.get("presupuesto"));
  out.put("tieneNinos",bool(row.get("tiene_ninos")));out.put("tieneOtrasMascotas",bool(row.get("tiene_otras_mascotas")));
  out.put("finalizado",Boolean.TRUE.equals(bool(row.get("finalizado"))));return out;
 }
 private static Boolean bool(Object v) {return v==null?null:v instanceof Boolean b?b:((Number)v).intValue()!=0;}
 @Transactional
 public Map<String,Object> save(long id,Map<String,Object> patch) {
  db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,id);
  Map<String,String> errors=new LinkedHashMap<>();
  patch.keySet().stream().filter(k->!FIELDS.contains(k)).forEach(k->errors.put(k,"campo no permitido"));
  Map<String,Object> v=get(id);v.putAll(patch);
  Object vivienda=v.get("vivienda");
  if(vivienda!=null) {if(!(vivienda instanceof String text) || text.trim().length()>120) errors.put("vivienda","texto de hasta 120 caracteres");
   else v.put("vivienda",text.trim().isEmpty()?null:text.trim());}
  for(String field:List.of("tieneNinos","tieneOtrasMascotas")) {
   if(v.get(field)!=null && !(v.get(field) instanceof Boolean)) errors.put(field,"Sí, No o sin responder");
  }
  if(!(v.get("finalizado") instanceof Boolean)) errors.put("finalizado","valor booleano obligatorio");
  decimal(v,"horasFuera",new BigDecimal("24"),errors);decimal(v,"presupuesto",new BigDecimal("999999999.99"),errors);
  if(Boolean.TRUE.equals(v.get("finalizado"))) {
   for(String field:FIELDS) if(!field.equals("finalizado") && v.get(field)==null) errors.put(field,"completa este campo para finalizar");
  }
  if(!errors.isEmpty()) throw new ApiException(400,"datos inválidos",errors);
  boolean exists=db.queryForObject("SELECT COUNT(*) FROM perfiles_adoptantes WHERE usuario_id=?",Long.class,id)>0;
  if(exists) db.update("UPDATE perfiles_adoptantes SET vivienda=?,horas_fuera=?,presupuesto=?,tiene_ninos=?,tiene_otras_mascotas=?,finalizado=? WHERE usuario_id=?",
   v.get("vivienda"),v.get("horasFuera"),v.get("presupuesto"),v.get("tieneNinos"),v.get("tieneOtrasMascotas"),v.get("finalizado"),id);
  else db.update("INSERT INTO perfiles_adoptantes (usuario_id,vivienda,horas_fuera,presupuesto,tiene_ninos,tiene_otras_mascotas,finalizado) VALUES (?,?,?,?,?,?,?)",
   id,v.get("vivienda"),v.get("horasFuera"),v.get("presupuesto"),v.get("tieneNinos"),v.get("tieneOtrasMascotas"),v.get("finalizado"));
  return get(id);
 }
 private void decimal(Map<String,Object> values,String field,BigDecimal max,Map<String,String> errors) {
  Object raw=values.get(field);if(raw==null) return;
  try {
   if(!(raw instanceof Number)) throw new IllegalArgumentException();
   BigDecimal number=new BigDecimal(raw.toString());
   if(number.signum()<0 || number.compareTo(max)>0 || number.stripTrailingZeros().scale()>2) throw new IllegalArgumentException();
   values.put(field,number);
  } catch(Exception e) {errors.put(field,"número entre 0 y "+max+", máximo dos decimales");}
 }
}
