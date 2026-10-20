package com.petly.catalogo;

import com.petly.common.exception.ApiException;
import com.petly.perfiles.PerfilService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Reglas explicables US-01/02. No decide ni predice una adopción. */
@Service
public class CompatibilidadService {
    public static final String VERSION = "petly-compat-v1";
    private final CatalogoService catalogo;
    private final PerfilService perfiles;

    public CompatibilidadService(CatalogoService catalogo, PerfilService perfiles) {
        this.catalogo = catalogo;
        this.perfiles = perfiles;
    }

    public Map<String, Object> listar(long userId, String search, String especie, String tamano,
            String temperamento, Integer min, Integer max, int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) throw new ApiException(400, "paginación inválida");
        Map<String, Object> profile = perfiles.get(userId);
        List<Map<String, Object>> items = new ArrayList<>();
        Map<String, Object> first = catalogo.search(search, especie, tamano, temperamento, min, max, 1, 100);
        int catalogTotal = ((Number) first.get("total")).intValue();
        items.addAll((List<Map<String, Object>>) first.get("items"));
        for (int p = 2; items.size() < catalogTotal; p++) {
            Map<String, Object> next = catalogo.search(search, especie, tamano, temperamento, min, max, p, 100);
            items.addAll((List<Map<String, Object>>) next.get("items"));
        }
        items.removeIf(item -> !"Disponible".equals(item.get("estado")));
        int total = items.size();
        items.replaceAll(item -> withScore(item, profile));
        items.sort(Comparator
                .comparing((Map<String, Object> item) -> ((Map<?, ?>) item.get("compatibilidad")).get("porcentaje") == null)
                .thenComparing((a, b) -> {
                    Object sa = ((Map<?, ?>) a.get("compatibilidad")).get("porcentaje");
                    Object sb = ((Map<?, ?>) b.get("compatibilidad")).get("porcentaje");
                    return sa == null ? 0 : Integer.compare(((Number) sb).intValue(), ((Number) sa).intValue());
                })
                .thenComparingLong(item -> ((Number) item.get("id")).longValue()));
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return Map.of("items", items.subList(from, to), "page", page, "pageSize", pageSize, "total", total,
                "orden", "compatibilidad_desc_id_asc", "versionReglas", VERSION);
    }

    static Map<String, Object> withScore(Map<String, Object> pet, Map<String, Object> profile) {
        Map<String, Object> item = new LinkedHashMap<>(pet);
        List<Map<String, Object>> criteria = new ArrayList<>();
        boolean finalized = Boolean.TRUE.equals(profile.get("finalizado"));
        add(criteria, "vivienda", "Vivienda", 30,
                finalized && profile.get("vivienda") instanceof String home && !home.isBlank()
                        && pet.get("tamano") != null && !"Por evaluar".equals(pet.get("tamano")),
                housing(profile, pet), "Compara el tipo de vivienda y espacio con el tamaño de la mascota.");
        add(criteria, "rutina", "Rutina", 35,
                finalized && profile.get("horasFuera") instanceof Number && knownEnergy(pet.get("energia")),
                routine(profile, pet), "Compara las horas fuera de casa con el nivel de energía.");
        add(criteria, "convivencia", "Convivencia", 35,
                finalized && profile.get("tieneNinos") instanceof Boolean
                        && profile.get("tieneOtrasMascotas") instanceof Boolean && knownTemperament(pet.get("temperamento")),
                coexistence(profile, pet), "Considera las respuestas del hogar y el temperamento registrado.");
        int coverage = criteria.stream().filter(c -> c.get("puntaje") != null)
                .mapToInt(c -> ((Number) c.get("peso")).intValue()).sum();
        boolean insufficient = coverage < 100;
        Integer score = null;
        if (!insufficient) {
            BigDecimal weighted = criteria.stream().map(c -> BigDecimal.valueOf(((Number) c.get("puntaje")).intValue())
                    .multiply(BigDecimal.valueOf(((Number) c.get("peso")).intValue()))
                    .divide(BigDecimal.valueOf(100)))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            score = weighted.setScale(0, RoundingMode.HALF_UP).intValue();
        }
        String message = !finalized ? "Completa tu perfil para calcular compatibilidad."
                : insufficient ? "Información insuficiente para calcular compatibilidad." : "Puntaje orientativo; el albergue decide cada adopción.";
        Map<String, Object> compatibility = new LinkedHashMap<>();
        compatibility.put("porcentaje", score); compatibility.put("puntaje", score);
        compatibility.put("cobertura", coverage); compatibility.put("datosInsuficientes", insufficient);
        compatibility.put("mensaje", message); compatibility.put("versionReglas", VERSION);
        compatibility.put("criterios", criteria);
        item.put("compatibilidad", compatibility);
        return item;
    }

    private static void add(List<Map<String, Object>> out, String id, String nombre, int weight,
            boolean known, int score, String explanation) {
        Map<String, Object> criterion = new LinkedHashMap<>();
        criterion.put("id", id); criterion.put("nombre", nombre); criterion.put("peso", weight);
        criterion.put("puntaje", known ? score : null);
        criterion.put("aporte", known ? BigDecimal.valueOf(score * weight).divide(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP) : null);
        criterion.put("explicacion", explanation);
        out.add(criterion);
    }

    private static int housing(Map<String, Object> p, Map<String, Object> pet) {
        String home = String.valueOf(p.get("vivienda")).toLowerCase();
        boolean apartment = home.contains("depart") || home.contains("departamento");
        boolean largeDog = "Perro".equals(pet.get("especie")) && "Grande".equals(pet.get("tamano"));
        return apartment && largeDog ? 60 : 100;
    }

    private static boolean knownEnergy(Object value) { return List.of("Baja", "Media", "Alta").contains(value); }
    private static boolean knownTemperament(Object value) { return value != null && !"Por evaluar".equals(value); }

    private static int routine(Map<String, Object> p, Map<String, Object> pet) {
        double hours = ((Number) p.get("horasFuera")).doubleValue();
        int timeBand = hours <= 4 ? 0 : hours <= 8 ? 1 : 2;
        String energy = String.valueOf(pet.get("energia"));
        int needBand = "Alta".equals(energy) ? 0 : "Media".equals(energy) ? 1 : 2;
        int distance = Math.abs(timeBand - needBand);
        return distance == 0 ? 100 : distance == 1 ? 70 : 35;
    }

    private static int coexistence(Map<String, Object> p, Map<String, Object> pet) {
        String temperament = String.valueOf(pet.get("temperamento"));
        int score = switch (temperament) {
            case "Sociable" -> 100; case "Tranquilo" -> 90; case "Juguetón" -> 80;
            case "Activo" -> 75; case "Independiente" -> 70; default -> 60;
        };
        if (Boolean.TRUE.equals(p.get("tieneNinos")) && "Tímido".equals(temperament)) score = 45;
        if (Boolean.TRUE.equals(p.get("tieneOtrasMascotas")) && "Activo".equals(temperament)) score = 65;
        return score;
    }
}
