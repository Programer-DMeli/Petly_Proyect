package com.petly_app.data.model
import java.math.BigDecimal
data class PerfilDto(val vivienda: String? = null, val horasFuera: BigDecimal? = null,
    val presupuesto: BigDecimal? = null, val tieneNinos: Boolean? = null,
    val tieneOtrasMascotas: Boolean? = null, val finalizado: Boolean = false) {
    fun draft() = PerfilDraft(vivienda.orEmpty(), horasFuera?.toPlainString().orEmpty(),
        presupuesto?.toPlainString().orEmpty(), tieneNinos, tieneOtrasMascotas, finalizado)
    companion object {
        fun from(d: PerfilDraft) = PerfilDto(d.vivienda.trim().takeIf { it.isNotEmpty() },
            d.horasFuera.trim().replace(',', '.').toBigDecimalOrNull(),
            d.presupuesto.trim().replace(',', '.').toBigDecimalOrNull(),d.tieneNinos,d.tieneOtrasMascotas,d.finalizado)
    }
}
