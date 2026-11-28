package com.petly.catalogo;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.util.UriUtils;
import com.petly.common.exception.ApiException;
@Service
public class CatalogoService {
 private static final Pattern FOTO_SUPABASE = Pattern.compile("mascotas/supabase/[0-9a-f]{32}\\.(?:jpg|png)");
 private static final Pattern FOTO_LOCAL = Pattern.compile("mascotas/[\\p{L}\\p{N}_. -]+\\.(?i:jpe?g|png|webp)");
 private static final ObjectMapper JSON = new ObjectMapper();
 private final JdbcTemplate db;
 private final String media;
 private final String catalogoPublico;

 public CatalogoService(JdbcTemplate db, @Value("${petly.media-base-url}") String media,
                        @Value("${petly.supabase-url:}") String supabaseUrl) {
  this.db = db;
  this.media = media.replaceAll("/+$", "");
  String proyecto = supabaseUrl.strip().replaceAll("/+$", "");
  this.catalogoPublico = proyecto.isEmpty() ? "" : proyecto + "/storage/v1/object/public/petly-catalogo";
 }
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
 private static final String FROM=" FROM mascotas_mascota m JOIN albergues a ON a.id=m.albergue_id ";
 private static final String AGE="(CASE WHEN m.edad_anos IS NULL AND m.edad_meses IS NULL THEN NULL ELSE COALESCE(m.edad_anos,0)*12+COALESCE(m.edad_meses,0) END)";
 public record MascotaDestacada(long id, String nombre, Integer edadMeses, String descripcion, String fotografiaUrl) {}

 public List<MascotaDestacada> destacadas() {
  return db.query("""
      SELECT m.id, m.nombre, %s AS edad_total, LEFT(m.descripcion, 240) AS descripcion, m.foto_principal
      %s
      WHERE a.activo = TRUE AND m.estado = 'DISPONIBLE'
      ORDER BY (NULLIF(m.foto_principal, '') IS NULL), m.id
      LIMIT 10
      """.formatted(AGE, FROM),
      (row, index) -> new MascotaDestacada(row.getLong("id"), row.getString("nombre"),
          row.getObject("edad_total", Integer.class), row.getString("descripcion"),
          fotografiaUrl(row.getString("foto_principal"))));
 }

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
  v.put("raza",row.get("raza"));
  v.put("especie",ESPECIES.get(row.get("especie")));v.put("tamano",TAMANOS.getOrDefault(row.get("tamanio"),"Por evaluar"));
  v.put("edadMeses",row.get("edad_total"));v.put("temperamento",TEMPERAMENTOS.getOrDefault(row.get("temperamento"),"Por evaluar"));
  v.put("energia",Map.of("BAJA","Baja","MEDIA","Media","ALTA","Alta").getOrDefault(String.valueOf(row.get("energia")),"Por evaluar"));
  v.put("descripcion",row.get("descripcion"));v.put("albergue",row.get("albergue_nombre"));v.put("albergueId",row.get("albergue_id"));v.put("ubicacion",row.get("ubicacion"));
  v.put("estado",Map.of("DISPONIBLE","Disponible","EN_PROCESO","En Proceso","ADOPTADO","Adoptado").get(row.get("estado")));
  String principal = (String) row.get("foto_principal");
  v.put("fotografiaUrl", fotografiaUrl(principal));
  v.put("fotografiasAdicionales", fotosAdicionales(row.get("fotos_adicionales"), principal));
  v.put("sexo", Map.of("MACHO", "Macho", "HEMBRA", "Hembra").get(String.valueOf(row.get("sexo"))));
  String salud = Objects.toString(row.get("estado_salud_general"), "").strip().toLowerCase(Locale.ROOT);
  v.put("saludConfirmada", Set.of("sano", "sana", "saludable").contains(salud));
  return v;
 }

 private List<String> fotosAdicionales(Object valor, String principal) {
  if (!(valor instanceof String json) || json.isBlank()) return List.of();
  Set<String> urls = new LinkedHashSet<>();
  try {
   var fotos = JSON.readTree(json);
   if (fotos == null || !fotos.isArray()) return List.of();
   for (var foto : fotos) {
    if (!foto.isTextual()) continue;
    String ruta = foto.textValue();
    if (!ruta.equals(principal) && (FOTO_LOCAL.matcher(ruta).matches() || FOTO_SUPABASE.matcher(ruta).matches())) {
     urls.add(fotografiaUrl(ruta));
    }
   }
  } catch (JsonProcessingException ignored) {
   return List.of();
  }
  return List.copyOf(urls);
 }

 private String fotografiaUrl(String ruta) {
  if (ruta == null || ruta.isBlank()) return null;
  if (!catalogoPublico.isEmpty() && FOTO_SUPABASE.matcher(ruta).matches()) {
   return catalogoPublico + "/" + ruta;
  }
  return media + "/" + UriUtils.encodePath(ruta, StandardCharsets.UTF_8);
 }
 public List<Map<String,Object>> shelters() {return db.queryForList("SELECT id,nombre,direccion FROM albergues WHERE activo=TRUE ORDER BY nombre,id");}
}
