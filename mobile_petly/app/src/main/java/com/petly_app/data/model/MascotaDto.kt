package com.petly_app.data.model

data class Mascota(
    val id: Long, val nombre: String, val especie: String, val tamano: String,
    val edadMeses: Int, val temperamento: String, val energia: String,
    val descripcion: String, val albergue: String, val ubicacion: String,
    val estado: String, val fotografiaUrl: String? = null
)

data class CatalogoFilters(
    val search: String = "", val especie: String? = null, val tamano: String? = null,
    val temperamento: String? = null, val edadMinimaMeses: String = "", val edadMaximaMeses: String = ""
) {
    fun ageError(): String? {
        val min = edadMinimaMeses.trim()
        val max = edadMaximaMeses.trim()
        if ((min.isNotEmpty() && (min.toIntOrNull() == null || min.toInt() < 0)) ||
            (max.isNotEmpty() && (max.toIntOrNull() == null || max.toInt() < 0))) {
            return "La edad se expresa en meses enteros, desde cero."
        }
        if (min.isNotEmpty() && max.isNotEmpty() && min.toInt() > max.toInt()) return "La edad mínima no puede superar la máxima."
        return null
    }
}

data class CatalogoOptions(val especies: List<String>, val tamanos: List<String>, val temperamentos: List<String>)
data class MascotaPage(val items: List<Mascota>, val page: Int, val total: Int, val pageSize: Int) {
    val totalPages: Int get() = if (total == 0) 1 else (total - 1) / pageSize + 1
}

// Consultas locales solo para la vista de prueba. Spring Boot realizará las consultas reales.
object DemoCatalogoQuery {
    fun search(data: List<Mascota>, filters: CatalogoFilters, page: Int, pageSize: Int): MascotaPage {
        require(filters.ageError() == null)
        require(page > 0 && pageSize > 0)
        val min = filters.edadMinimaMeses.trim().toIntOrNull()
        val max = filters.edadMaximaMeses.trim().toIntOrNull()
        val text = filters.search.trim()
        val matching = data.filter {
            (text.isEmpty() || it.nombre.contains(text, ignoreCase = true) || it.albergue.contains(text, ignoreCase = true)) &&
                (filters.especie == null || it.especie == filters.especie) &&
                (filters.tamano == null || it.tamano == filters.tamano) &&
                (filters.temperamento == null || it.temperamento == filters.temperamento) &&
                (min == null || it.edadMeses >= min) && (max == null || it.edadMeses <= max)
        }.sortedBy { it.id }
        val pages = if (matching.isEmpty()) 1 else (matching.size - 1) / pageSize + 1
        val safePage = page.coerceAtMost(pages)
        return MascotaPage(matching.drop((safePage - 1) * pageSize).take(pageSize), safePage, matching.size, pageSize)
    }
}
