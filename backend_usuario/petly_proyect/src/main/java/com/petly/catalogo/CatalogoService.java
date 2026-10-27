package com.petly.catalogo;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.petly.common.exception.ApiException;
@Service
public class CatalogoService {
 private final JdbcTemplate db;private final String media;
 public CatalogoService(JdbcTemplate db,@Value("${petly.media-base-url}") String media) {this.db=db;this.media=media;}
 public static final Map<String,String> ESPECIES=new LinkedHashMap<>();
 public static final Map<String,String> TAMANOS=new LinkedHashMap<>();
 public static final Map<String,String> TEMPERAMENTOS=new LinkedHashMap<>();
 static {
  ESPECIES.put("PERRO","Perro");ESPECIES.put("GATO","Gato");ESPECIES.put("OTRO","Otro");
  TAMANOS.put("PEQUENO","Pequeño");TAMANOS.put("MEDIANO","Mediano");TAMANOS.put("GRANDE","Grande");
  TEMPERAMENTOS.put("TRANQUILO","Tranquilo");TEMPERAMENTOS.put("ACTIVO","Activo");TEMPERAMENTOS.put("JUGUETON","Juguetón");
  TEMPERAMENTOS.put("TIMIDO","Tímido");TEMPERAMENTOS.put("SOCIABLE","Sociable");TEMPERAMENTOS.put("INDEPENDIENTE","Independiente");TEMPERAMENTOS.put("POR_EVALUAR","Por evaluar");
 }
 public Map<String,Object> options() {return Map.of("especies",ESPECIES.values(),"tamanos",TAMANOS.values(),"temperamentos",TEMPERAMENTOS.values());}
 private static final String FROM=" FROM mascotas_mascota m JOIN albergues_albergue a ON a.id=m.albergue_id ";
 private static final String AGE="(CASE WHEN m.edad_anos IS NULL AND m.edad_meses IS NULL THEN NULL ELSE COALESCE(m.edad_anos,0)*12+COALESCE(m.edad_meses,0) END)";
 public Map<String,Object> search(String search,String especie,String tamano,String temperamento,Integer min,Integer max,int page,int size) {
  if(page<1 || page>1000000 || size<1 || size>100 || search.length()>120 || (min!=null&&(min<0||min>600)) || (max!=null&&(max<0||max>600)) || (min!=null&&max!=null&&min>max)) throw new ApiException(400,"filtros inválidos");
  StringBuilder where=new StringBuilder(" WHERE a.activo=TRUE");List<Object> args=new ArrayList<>();
  add(where,args,"m.especie",especie,ESPECIES);add(where,args,"m.tamanio",tamano,TAMANOS);add(where,args,"m.temperamento",temperamento,TEMPERAMENTOS);
  if(!search.isBlank()) {where.append(" AND (LOWER(m.nombre) LIKE ? ESCAPE '!' OR LOWER(a.nombre) LIKE ? ESCAPE '!')");
   String text="%"+search.trim().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";args.add(text);args.add(text);}
  if(min!=null) {where.append(" AND "+AGE+">=?");args.add(min);}
  if(max!=null) {where.append(" AND "+AGE+"<=?");args.add(max);}
  long total=db.queryForObject("SELECT COUNT(*)"+FROM+where,Long.class,args.toArray());
  args.add(size);args.add((long)(page-1)*size);
  var rows=db.queryForList("SELECT m.*, a.nombre AS albergue_nombre,a.direccion AS ubicacion,"+AGE+" AS edad_total"+FROM+where+" ORDER BY m.id ASC LIMIT ? OFFSET ?",args.toArray());
  return Map.of("items",rows.stream().map(this::pet).toList(),"page",page,"pageSize",size,"total",total);
 }
 private void add(StringBuilder where,List<Object> args,String column,String filter,Map<String,String> choices) {
  if(filter==null || filter.isBlank()) return;
  String code=choices.entrySet().stream().filter(e->e.getValue().equals(filter)||e.getKey().equals(filter)).map(Map.Entry::getKey).findFirst().orElseThrow(()->new ApiException(400,"categoría inválida"));
  where.append(" AND "+column+"=?");args.add(code);
 }
 public Map<String,Object> detail(long id) {
  var rows=db.queryForList("SELECT m.*,a.nombre AS albergue_nombre,a.direccion AS ubicacion,"+AGE+" AS edad_total"+FROM+" WHERE m.id=? AND a.activo=TRUE",id);
  if(rows.isEmpty()) throw new ApiException(404,"mascota no encontrada");return pet(rows.get(0));
 }
 private Map<String,Object> pet(Map<String,Object> row) {
  Map<String,Object> v=new LinkedHashMap<>();v.put("id",row.get("id"));v.put("nombre",row.get("nombre"));
  v.put("especie",ESPECIES.get(row.get("especie")));v.put("tamano",TAMANOS.getOrDefault(row.get("tamanio"),"Por evaluar"));
  v.put("edadMeses",row.get("edad_total"));v.put("temperamento",TEMPERAMENTOS.getOrDefault(row.get("temperamento"),"Por evaluar"));
  v.put("energia",Map.of("BAJA","Baja","MEDIA","Media","ALTA","Alta").getOrDefault(String.valueOf(row.get("energia")),"Por evaluar"));
  v.put("descripcion",row.get("descripcion"));v.put("albergue",row.get("albergue_nombre"));v.put("albergueId",row.get("albergue_id"));v.put("ubicacion",row.get("ubicacion"));
  v.put("estado",Map.of("DISPONIBLE","Disponible","EN_PROCESO","En Proceso","ADOPTADO","Adoptado").get(row.get("estado")));
  String photo=(String)row.get("foto_principal");v.put("fotografiaUrl",photo==null||photo.isBlank()?null:media.replaceAll("/$","")+"/"+photo);return v;
 }
 public List<Map<String,Object>> shelters() {return db.queryForList("SELECT id,nombre,direccion FROM albergues_albergue WHERE activo=TRUE ORDER BY nombre,id");}
}
