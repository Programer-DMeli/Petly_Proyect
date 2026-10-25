package com.petly.catalogo;

import com.petly.common.exception.ApiException;
import java.util.Map;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lee y actualiza los pesos globales persistidos en la tabla propiedad de Django. */
@Service
public class ReglasCompatibilidadService {
    private final JdbcTemplate db;

    public ReglasCompatibilidadService(JdbcTemplate db) {
        this.db = db;
    }

    public Reglas obtener() {
        try {
            return db.queryForObject(
                    "SELECT peso_vivienda,peso_rutina,peso_convivencia,version_reglas "
                            + "FROM mascotas_reglacompatibilidad WHERE id=1",
                    (rs, row) -> new Reglas(
                            rs.getInt("peso_vivienda"), rs.getInt("peso_rutina"),
                            rs.getInt("peso_convivencia"), rs.getInt("version_reglas")),
                    new Object[]{});
        } catch (EmptyResultDataAccessException exception) {
            throw new ApiException(503, "La configuración de compatibilidad no está inicializada.");
        }
    }

    @Transactional
    public Reglas actualizar(int vivienda, int rutina, int convivencia, long adminSpringId) {
        if (vivienda < 0 || rutina < 0 || convivencia < 0
                || vivienda > 100 || rutina > 100 || convivencia > 100
                || vivienda + rutina + convivencia != 100) {
            throw new ApiException(400, "Los pesos deben estar entre 0 y 100 y sumar 100.");
        }
        Integer bloqueado = db.queryForObject(
                "SELECT id FROM mascotas_reglacompatibilidad WHERE id=1 FOR UPDATE", Integer.class);
        if (bloqueado == null) {
            throw new ApiException(503, "La configuración de compatibilidad no está inicializada.");
        }
        db.update("UPDATE mascotas_reglacompatibilidad SET peso_vivienda=?,peso_rutina=?,"
                        + "peso_convivencia=?,version_reglas=version_reglas+1,"
                        + "actualizado_por_spring_id=?,actualizado_en=CURRENT_TIMESTAMP WHERE id=1",
                vivienda, rutina, convivencia, adminSpringId);
        return obtener();
    }

    public record Reglas(int vivienda, int rutina, int convivencia, int version) {
        public String versionNombre() {
            return "petly-compat-v" + version;
        }

        public Map<String, Object> respuesta() {
            return Map.of(
                    "pesos", Map.of("vivienda", vivienda, "rutina", rutina, "convivencia", convivencia),
                    "versionReglas", versionNombre());
        }
    }
}
