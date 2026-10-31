package com.petly_app.data.model

import java.math.BigDecimal

data class PerfilDraft(
    val vivienda: String = "",
    val horasFuera: String = "",
    val presupuesto: String = "",
    val tieneNinos: Boolean? = null,
    val tieneOtrasMascotas: Boolean? = null,
    val finalizado: Boolean = false,
    val espacioExterior: String = "",
    val tiempoPaseos: String = "",
    val rutinaHabitual: String = "",
    val ninosDetalle: String = "",
    val otrosAnimales: String = "",
    val experienciaMascotas: String = "",
    val cuidadosAsumibles: List<String> = emptyList(),
    val fotosViviendaUrls: List<String> = emptyList()
)

object PerfilValidation {
    fun validate(form: PerfilDraft, finalizar: Boolean): Map<String, String> = buildMap {
        if (finalizar && form.vivienda.isBlank()) put("vivienda", "Indica tu tipo de vivienda.")
        if (form.vivienda.length > 120) put("vivienda", "Usa como máximo 120 caracteres.")
        validateNumber(form.horasFuera, "horasFuera", finalizar, BigDecimal("24"))
        validateNumber(form.presupuesto, "presupuesto", finalizar, null)
        if (finalizar && form.tieneNinos == null && form.ninosDetalle.isBlank()) put("ninos", "Selecciona una opción sobre presencia de niños.")
        if (finalizar && form.tieneOtrasMascotas == null && form.otrosAnimales.isBlank()) put("mascotas", "Selecciona una opción sobre otros animales.")
    }

    private fun MutableMap<String, String>.validateNumber(raw: String, field: String, required: Boolean, maximum: BigDecimal?) {
        val value = raw.trim().replace(',', '.')
        if (value.isEmpty()) { if (required) put(field, "Completa este campo."); return }
        val number = if (Regex("\\d{1,9}(\\.\\d{1,2})?").matches(value)) value.toBigDecimalOrNull() else null
        if (number == null || number < BigDecimal.ZERO) {
            put(field, "Ingresa un número positivo o cero, con hasta dos decimales.")
        } else if (maximum != null && number > maximum) put(field, "Las horas deben estar entre 0 y 24.")
    }
}
