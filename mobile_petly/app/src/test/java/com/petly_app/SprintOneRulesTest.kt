package com.petly_app

import com.petly_app.data.model.*
import org.junit.Assert.*
import org.junit.Test

class SprintOneRulesTest {
    private val complete = PerfilDraft("Departamento", "8", "150,50", false, true)
    private val mascotas = listOf(
        Mascota(1, "Luna", "Perro", "Mediano", 24, "Tranquilo", "Media", "", "A", "Lima", "Disponible"),
        Mascota(2, "Milo", "Gato", "Pequeño", 8, "Por evaluar", "Por evaluar", "", "B", "Lima", "Disponible"),
        Mascota(3, "Kira", "Perro", "Mediano", 36, "Por evaluar", "Por evaluar", "", "A", "Lima", "En Proceso"),
        Mascota(4, "Nina", "Gato", "Pequeño", 24, "Tranquilo", "Baja", "", "B", "Lima", "Adoptado")
    )

    @Test fun partialCanOmitFields() { assertTrue(PerfilValidation.validate(PerfilDraft(), false).isEmpty()) }
    @Test fun finalRequiresAllAnswers() {
        assertEquals(setOf("vivienda", "horasFuera", "presupuesto", "ninos", "mascotas"), PerfilValidation.validate(PerfilDraft(), true).keys)
    }
    @Test fun noIsAValidAnswer() { assertTrue(PerfilValidation.validate(complete, true).isEmpty()) }
    @Test fun missingChildrenIsNotNo() { assertTrue(PerfilValidation.validate(complete.copy(tieneNinos = null), true).containsKey("ninos")) }
    @Test fun draftRejectsInvalidHours() { assertTrue(PerfilValidation.validate(PerfilDraft(horasFuera = "25"), false).containsKey("horasFuera")) }
    @Test fun hoursBoundariesAreValid() {
        assertTrue(PerfilValidation.validate(complete.copy(horasFuera = "0"), true).isEmpty())
        assertTrue(PerfilValidation.validate(complete.copy(horasFuera = "24"), true).isEmpty())
    }
    @Test fun negativeBudgetIsInvalid() { assertTrue(PerfilValidation.validate(complete.copy(presupuesto = "-1"), true).containsKey("presupuesto")) }
    @Test fun malformedBudgetIsInvalid() { assertTrue(PerfilValidation.validate(complete.copy(presupuesto = "1.2.3"), true).containsKey("presupuesto")) }
    @Test fun commaDecimalIsValid() { assertTrue(PerfilValidation.validate(complete, true).isEmpty()) }
    @Test fun nonFiniteValuesAreInvalid() { assertTrue(PerfilValidation.validate(complete.copy(presupuesto = "NaN"), true).containsKey("presupuesto")) }
    @Test fun combinedFiltersUseIntersection() {
        val page = DemoCatalogoQuery.search(mascotas, CatalogoFilters(especie = "Perro", tamano = "Mediano", temperamento = "Por evaluar", edadMinimaMeses = "30"), 1, 3)
        assertEquals(listOf(3L), page.items.map { it.id })
    }
    @Test fun pendingEvaluationRemainsDistinct() {
        val page = DemoCatalogoQuery.search(mascotas, CatalogoFilters(temperamento = "Por evaluar"), 1, 3)
        assertEquals(listOf(2L, 3L), page.items.map { it.id })
    }
    @Test fun searchIgnoresCaseAndOuterSpaces() {
        assertEquals(listOf(1L), DemoCatalogoQuery.search(mascotas, CatalogoFilters(search = " LUNA "), 1, 3).items.map { it.id })
    }
    @Test fun ageBoundsAreInclusive() {
        assertEquals(listOf(1L, 4L), DemoCatalogoQuery.search(mascotas, CatalogoFilters(edadMinimaMeses = "24", edadMaximaMeses = "24"), 1, 3).items.map { it.id })
    }
    @Test fun paginationKeepsFilters() {
        val filters = CatalogoFilters(especie = "Gato")
        val first = DemoCatalogoQuery.search(mascotas, filters, 1, 1)
        val second = DemoCatalogoQuery.search(mascotas, filters, 2, 1)
        assertEquals(2, first.total)
        assertEquals(2, first.totalPages)
        assertEquals(listOf(2L), first.items.map { it.id })
        assertEquals(listOf(4L), second.items.map { it.id })
    }
    @Test fun noMatchesProducesEmptyState() {
        val page = DemoCatalogoQuery.search(mascotas, CatalogoFilters(search = "inexistente"), 1, 3)
        assertTrue(page.items.isEmpty()); assertEquals(0, page.total); assertEquals(1, page.totalPages)
    }
    @Test fun invertedAgesAreInvalid() { assertNotNull(CatalogoFilters(edadMinimaMeses = "20", edadMaximaMeses = "10").ageError()) }
    @Test fun overflowingAgeIsInvalid() { assertNotNull(CatalogoFilters(edadMinimaMeses = "99999999999999").ageError()) }
}
