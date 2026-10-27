package com.petly.perfiles;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.petly.catalogo.CatalogoService;
import com.petly.common.exception.ApiException;

@Service
public class PreferenciasService {
    private final JdbcTemplate db;
    private final CatalogoService catalogo;
    public static final List<String> DIAS = List.of("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo");
    public record Franja(Long id, String diaSemana, String horaInicio, String horaFin, String descripcion) {}
    public PreferenciasService(JdbcTemplate db, CatalogoService catalogo) { this.db = db; this.catalogo = catalogo; }
    public List<Map<String,Object>> favoritos(long user) {
        var ids = db.queryForList("SELECT mascota_id FROM favoritos_mascotas WHERE usuario_id=? ORDER BY creado_en,mascota_id", Long.class, user);
        List<Map<String,Object>> result = new ArrayList<>();
        for (long id : ids) {
            try { result.add(catalogo.detail(id)); }
            catch (ApiException e) {
                Map<String,Object> unavailable = new LinkedHashMap<>();
                for (String field : List.of("especie","tamano","temperamento","energia","descripcion","albergue","ubicacion")) unavailable.put(field, "No disponible");
                unavailable.put("edadMeses", null); unavailable.put("fotografiaUrl", null);
                unavailable.put("id", id); unavailable.put("nombre", "Mascota no disponible"); unavailable.put("estado", "No disponible");
                result.add(unavailable);
            }
        }
        return result;
    }
    @Transactional public void favorito(long user, long mascota, boolean agregar) {
        db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE", Long.class, user);
        if (!agregar) { db.update("DELETE FROM favoritos_mascotas WHERE usuario_id=? AND mascota_id=?", user, mascota); return; }
        catalogo.detail(mascota);
        if (db.queryForObject("SELECT COUNT(*) FROM favoritos_mascotas WHERE usuario_id=? AND mascota_id=?", Long.class,user,mascota)==0)
            db.update("INSERT INTO favoritos_mascotas(usuario_id,mascota_id) VALUES (?,?)",user,mascota);
    }
    public List<Franja> horarios(long user) {
        return db.query("SELECT * FROM disponibilidad_adoptante WHERE usuario_id=? ORDER BY id", (rs,n)->new Franja(rs.getLong("id"), rs.getString("dia_semana"), rs.getString("hora_inicio"), rs.getString("hora_fin"), rs.getString("descripcion")), user);
    }
    public static void validar(List<Franja> rows) {
        if (rows == null || rows.size()>100) throw new ApiException(400,"Máximo 100 franjas semanales.");
        for (int i=0; i<rows.size(); i++) {
            Franja f = rows.get(i);
            if(f==null || !DIAS.contains(f.diaSemana()) || f.horaInicio()==null || f.horaFin()==null || !f.horaInicio().matches("[0-9]{2}:[0-9]{2}") || !f.horaFin().matches("[0-9]{2}:[0-9]{2}") || (f.descripcion()!=null && f.descripcion().length()>300)) throw new ApiException(400,"Franja horaria inválida.");
            try {
                LocalTime start = LocalTime.parse(f.horaInicio()), end = LocalTime.parse(f.horaFin());
                if(!start.isBefore(end)) throw new ApiException(400,"El inicio debe ser anterior al fin.");
                for(int j=0;j<i;j++) {
                    Franja other=rows.get(j);
                    if(f.diaSemana().equals(other.diaSemana()) && start.isBefore(LocalTime.parse(other.horaFin())) && LocalTime.parse(other.horaInicio()).isBefore(end)) throw new ApiException(400,"Las franjas del mismo día no pueden solaparse.");
                }
            } catch(DateTimeParseException e) { throw new ApiException(400,"Hora inválida."); }
        }
    }
    @Transactional public List<Franja> guardar(long user,List<Franja> rows) {
        validar(rows);
        db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,user);
        db.update("DELETE FROM disponibilidad_adoptante WHERE usuario_id=?",user);
        for(Franja f:rows) db.update("INSERT INTO disponibilidad_adoptante(usuario_id,dia_semana,hora_inicio,hora_fin,descripcion) VALUES (?,?,?,?,?)",user,f.diaSemana(),f.horaInicio(),f.horaFin(),f.descripcion()==null?"":f.descripcion());
        return horarios(user);
    }
    @Transactional public void eliminar(long user,long id) { db.queryForObject("SELECT id FROM usuarios WHERE id=? FOR UPDATE",Long.class,user); db.update("DELETE FROM disponibilidad_adoptante WHERE id=? AND usuario_id=?",id,user); }
}
