package com.petly.catalogo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class CatalogoServiceFotoTest {
    @Test
    void mantieneElContratoFotografiaUrlParaFotosRemotasHistoricasYVacias() {
        Map<String, Object> row = new HashMap<>(Map.of(
            "especie", "GATO", "tamanio", "MEDIANO", "temperamento", "SOCIABLE", "estado", "DISPONIBLE"
        ));
        JdbcTemplate db = new JdbcTemplate() {
            @Override
            public List<Map<String, Object>> queryForList(String sql, Object... args) {
                return List.of(row);
            }
        };
        var servicio = new CatalogoService(db, "https://django.example/media/", "https://supabase.example/");

        String remota = "mascotas/supabase/0123456789abcdef0123456789abcdef.png";
        row.put("foto_principal", remota);
        assertEquals("https://supabase.example/storage/v1/object/public/petly-catalogo/" + remota,
                     servicio.detail(1).get("fotografiaUrl"));

        row.put("foto_principal", "mascotas/historica.jpg");
        assertEquals("https://django.example/media/mascotas/historica.jpg", servicio.detail(1).get("fotografiaUrl"));

        row.put("sexo", "HEMBRA");
        row.put("estado_salud_general", " Sana ");
        row.put("fotos_adicionales", """
            ["mascotas/historica.jpg", "mascotas/foto extra.png",
             "mascotas/veterinaria/vacunacion/privada.png", "https://otro.example/foto.jpg",
             "mascotas/foto extra.png"]
            """);
        var ficha = servicio.detail(1);
        assertEquals("Hembra", ficha.get("sexo"));
        assertEquals(true, ficha.get("saludConfirmada"));
        assertEquals(List.of("https://django.example/media/mascotas/foto%20extra.png"),
                     ficha.get("fotografiasAdicionales"));

        row.put("estado_salud_general", "Requiere atención");
        assertEquals(false, servicio.detail(1).get("saludConfirmada"));
        row.put("foto_principal", null);
        assertNull(servicio.detail(1).get("fotografiaUrl"));
    }
}
