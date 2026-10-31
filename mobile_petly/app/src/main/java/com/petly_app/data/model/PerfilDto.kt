package com.petly_app.data.model

import java.math.BigDecimal

data class PerfilDto(
    val vivienda: String? = null,
    val horasFuera: BigDecimal? = null,
    val presupuesto: BigDecimal? = null,
    val tieneNinos: Boolean? = null,
    val tieneOtrasMascotas: Boolean? = null,
    val finalizado: Boolean = false
) {
    fun draft() = PerfilDraft(
        vivienda = vivienda.orEmpty(),
        horasFuera = horasFuera?.toPlainString().orEmpty(),
        presupuesto = presupuesto?.toPlainString().orEmpty(),
        tieneNinos = tieneNinos,
        tieneOtrasMascotas = tieneOtrasMascotas,
        finalizado = finalizado
    )

    companion object {
        fun from(d: PerfilDraft) = PerfilDto(
            vivienda = d.vivienda.trim().takeIf { it.isNotEmpty() },
            horasFuera = d.horasFuera.trim().replace(',', '.').toBigDecimalOrNull(),
            presupuesto = d.presupuesto.trim().replace(',', '.').toBigDecimalOrNull(),
            tieneNinos = d.tieneNinos,
            tieneOtrasMascotas = d.tieneOtrasMascotas,
            finalizado = d.finalizado
        )
    }
}
