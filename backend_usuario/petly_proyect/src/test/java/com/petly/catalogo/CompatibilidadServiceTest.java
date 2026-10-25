package com.petly.catalogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.petly.perfiles.PerfilService;

class CompatibilidadServiceTest {
    @Test
    void listaOrdenaPuntajeDescendenteEmpatesPorIdYDatosIncompletosAlFinal() {
        CompatibilidadService service = serviceWith(petWithId(8, "Grande", "Alta", "Tímido", "Perro"),
                petWithId(4, "Mediano", "Media", "Sociable", "Perro"),
                petWithId(6, "Mediano", "Media", "Sociable", "Perro"),
                petWithId(2, "", "POR_EVALUAR", "POR_EVALUAR", "Perro"));

        Map<String, Object> result = service.listar(7, "", null, null, null, null, null, 1, 10);

        assertEquals(List.of(4L, 6L, 8L, 2L), ids(result));
        assertNull(compatibility((Map<?, ?>) ((List<?>) result.get("items")).get(3)).get("porcentaje"));
    }

    @Test
    void perfilYMascotaQueCoincidenDanCienPorCiento() {
        Map<String, Object> result = CompatibilidadService.withScore(
                pet("Grande", "Alta", "Sociable", "Perro"), profile(true, "Casa", 3, false, false));
        Map<?, ?> score = (Map<?, ?>) result.get("compatibilidad");
        assertEquals(100, score.get("porcentaje"));
        assertEquals(false, score.get("datosInsuficientes"));
        assertEquals(CompatibilidadService.VERSION, score.get("versionReglas"));
    }

    @Test
    void menorAjusteDaPuntajeMenorYExplicable() {
        Map<String, Object> result = CompatibilidadService.withScore(
                pet("Grande", "Alta", "Tímido", "Perro"), profile(true, "Departamento", 10, true, true));
        Map<?, ?> score = (Map<?, ?>) result.get("compatibilidad");
        assertEquals(46, score.get("porcentaje"));
        assertEquals(60, ((Map<?, ?>) ((java.util.List<?>) score.get("criterios")).get(0)).get("puntaje"));
    }

    @Test
    void reglaViviendaCoincideConDjangoYNoDependeDePatio() {
        var department = profile(true, "Departamento", 3, false, false);
        var house = profile(true, "Casa", 3, false, false);
        var largeDog = pet("Grande", "Media", "Sociable", "Perro");
        var smallDog = pet("Pequeño", "Media", "Sociable", "Perro");

        assertEquals(60, CompatibilidadService.housing(department, largeDog));
        assertEquals(100, CompatibilidadService.housing(house, largeDog));
        assertEquals(100, CompatibilidadService.housing(department, smallDog));
    }

    @Test
    void perfilIncompletoNoRecibePuntajePerfecto() {
        Map<String, Object> result = CompatibilidadService.withScore(
                pet("Grande", "Alta", "Sociable", "Perro"), profile(false, "Casa", 3, false, false));
        Map<?, ?> score = (Map<?, ?>) result.get("compatibilidad");
        assertNull(score.get("porcentaje"));
        assertEquals(true, score.get("datosInsuficientes"));
        assertEquals(0, score.get("cobertura"));
    }

    @Test
    void perfilParcialSinRutinaDevuelveDatosInsuficientesSinFallar() {
        Map<String, Object> partial = new java.util.HashMap<>();
        partial.put("finalizado", false);
        partial.put("vivienda", null);
        partial.put("horasFuera", null);
        partial.put("tieneNinos", null);
        partial.put("tieneOtrasMascotas", null);

        Map<?, ?> score = (Map<?, ?>) CompatibilidadService.withScore(
                pet("Grande", "Alta", "Sociable", "Perro"), partial).get("compatibilidad");

        assertNull(score.get("porcentaje"));
        assertEquals(0, score.get("cobertura"));
        assertEquals(true, score.get("datosInsuficientes"));
    }

    private Map<String, Object> pet(String size, String energy, String temperament, String species) {
        return petWithId(9, size, energy, temperament, species);
    }
    private Map<String, Object> petWithId(long id, String size, String energy, String temperament, String species) {
        Map<String, Object> pet = new LinkedHashMap<>();
        pet.put("id", id); pet.put("nombre", "Luna"); pet.put("tamano", size);
        pet.put("energia", energy); pet.put("temperamento", temperament);
        pet.put("especie", species); pet.put("estado", "Disponible");
        return pet;
    }
    private CompatibilidadService serviceWith(Map<String, Object>... pets) {
        CatalogoService catalogo = new CatalogoService(null, "") {
            @Override
            public Map<String, Object> search(String search, String especie, String tamano, String temperamento,
                    Integer min, Integer max, int page, int size) {
                return Map.of("items", List.of(pets), "total", pets.length);
            }
        };
        PerfilService perfiles = new PerfilService(null) {
            @Override
            public Map<String, Object> get(long id) {
                return profile(true, "Casa", 3, false, false);
            }
        };
        return new CompatibilidadService(catalogo, perfiles, defaultRules());
    }
    private ReglasCompatibilidadService defaultRules() {
        return new ReglasCompatibilidadService(null) {
            @Override
            public Reglas obtener() {
                return new Reglas(30, 35, 35, 1);
            }
        };
    }
    private List<Long> ids(Map<String, Object> result) {
        return ((List<Map<String, Object>>) result.get("items")).stream()
                .map(item -> ((Number) item.get("id")).longValue()).toList();
    }
    private Map<?, ?> compatibility(Map<?, ?> pet) {
        return (Map<?, ?>) pet.get("compatibilidad");
    }
    private Map<String, Object> profile(boolean finalized, String home, int hours, boolean kids, boolean pets) {
        return Map.of("finalizado", finalized, "vivienda", home, "horasFuera", hours,
                "tieneNinos", kids, "tieneOtrasMascotas", pets);
    }
}
