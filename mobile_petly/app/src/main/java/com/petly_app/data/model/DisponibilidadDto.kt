package com.petly_app.data.model

/**
 * Franja horaria semanal de disponibilidad del adoptante (US-20).
 * Zona horaria: America/Lima.
 */
data class FranjaHoraria(
    val id: Long? = null,
    val diaSemana: String,
    val horaInicio: String,
    val horaFin: String,
    val descripcion: String = ""
)

object DisponibilidadValidation {
    val diasSemana = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    fun parseMinutes(raw: String): Int? {
        val clean = raw.trim()
        if (clean.isBlank()) return null
        val parts = clean.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }

    fun validateFranja(franja: FranjaHoraria): String? {
        if (franja.diaSemana.isBlank() || !diasSemana.contains(franja.diaSemana)) {
            return "Selecciona un día de la semana válido."
        }
        val start = parseMinutes(franja.horaInicio) ?: return "Ingresa una hora de inicio válida (ej. 09:00)."
        val end = parseMinutes(franja.horaFin) ?: return "Ingresa una hora de fin válida (ej. 12:00)."

        if (start >= end) {
            return "La hora de inicio debe ser anterior a la hora de fin."
        }
        return null
    }

    fun validateNoOverlap(existing: List<FranjaHoraria>, newFranja: FranjaHoraria): String? {
        val err = validateFranja(newFranja)
        if (err != null) return err

        val newStart = parseMinutes(newFranja.horaInicio)!!
        val newEnd = parseMinutes(newFranja.horaFin)!!

        for (item in existing) {
            if (item.id != newFranja.id && item.diaSemana.equals(newFranja.diaSemana, ignoreCase = true)) {
                val itemStart = parseMinutes(item.horaInicio)
                val itemEnd = parseMinutes(item.horaFin)
                if (itemStart != null && itemEnd != null) {
                    val maxStart = maxOf(newStart, itemStart)
                    val minEnd = minOf(newEnd, itemEnd)
                    if (maxStart < minEnd) {
                        return "Existe un solapamiento de horario con la franja (${item.horaInicio} - ${item.horaFin}) para el día ${newFranja.diaSemana}."
                    }
                }
            }
        }
        return null
    }
}
