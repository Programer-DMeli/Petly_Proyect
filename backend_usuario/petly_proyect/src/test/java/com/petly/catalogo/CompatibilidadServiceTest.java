package com.petly.catalogo;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CompatibilidadServiceTest {
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
        return Map.of("id", 9, "nombre", "Luna", "tamano", size, "energia", energy,
                "temperamento", temperament, "especie", species, "estado", "Disponible");
    }
    private Map<String, Object> profile(boolean finalized, String home, int hours, boolean kids, boolean pets) {
        return Map.of("finalizado", finalized, "vivienda", home, "horasFuera", hours,
                "tieneNinos", kids, "tieneOtrasMascotas", pets);
    }
}
