package com.petly.catalogo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.petly.perfiles.PerfilService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CompatibilidadServicePaginationTest {
    @Test
    void ordenaAntesDePaginarYConservaLosFiltrosDelCatalogo() {
        List<Object[]> filtrosRecibidos = new ArrayList<>();
        CatalogoService catalogo = new CatalogoService(null, "") {
            @Override
            public Map<String, Object> search(String search, String especie, String tamano, String temperamento,
                    Integer min, Integer max, int page, int size) {
                filtrosRecibidos.add(new Object[] { search, especie, tamano, temperamento, min, max, page, size });
                return Map.of("items", List.of(
                        pet(12, "Mediano", "Media", "Sociable"),
                        pet(3, "Mediano", "Media", "Sociable"),
                        pet(9, "Grande", "Alta", "Tímido")), "total", 3);
            }
        };
        PerfilService perfiles = new PerfilService(null) {
            @Override
            public Map<String, Object> get(long id) {
                return profile();
            }
        };
        ReglasCompatibilidadService reglas = new ReglasCompatibilidadService(null) {
            @Override
            public Reglas obtener() {
                return new Reglas(30, 35, 35, 1);
            }
        };
        CompatibilidadService service = new CompatibilidadService(catalogo, perfiles, reglas);

        Map<String, Object> result = service.listar(7, "Luna", "Perro", "Mediano", "Sociable", 1, 60, 2, 2);

        assertEquals(List.of(9L), ids(result));
        assertEquals(2, result.get("page"));
        assertEquals(3, result.get("total"));
        assertEquals(1, filtrosRecibidos.size());
        assertEquals(List.of("Luna", "Perro", "Mediano", "Sociable", 1, 60, 1, 100),
                java.util.Arrays.asList(filtrosRecibidos.get(0)));
    }

    private Map<String, Object> profile() {
        return Map.of("finalizado", true, "vivienda", "Casa", "horasFuera", 3,
                "tieneNinos", false, "tieneOtrasMascotas", false);
    }

    private Map<String, Object> pet(long id, String size, String energy, String temperament) {
        Map<String, Object> pet = new LinkedHashMap<>();
        pet.put("id", id);
        pet.put("nombre", "Luna");
        pet.put("tamano", size);
        pet.put("energia", energy);
        pet.put("temperamento", temperament);
        pet.put("especie", "Perro");
        pet.put("estado", "Disponible");
        return pet;
    }

    private List<Long> ids(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("items")).stream()
                .map(item -> ((Number) item.get("id")).longValue()).toList();
    }
}
