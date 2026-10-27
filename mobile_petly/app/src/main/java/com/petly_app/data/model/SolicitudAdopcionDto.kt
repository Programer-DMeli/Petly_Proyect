package com.petly_app.data.model

data class SolicitudAdopcionDraft(
    val mascotaId: Long = 0L,
    val mascotaNombre: String = "",
    val nombreCompleto: String = "",
    val dni: String = "",
    val dniVerificado: Boolean = false,
    val direccionHogar: String = "",
    val telefonoContacto: String = "",
    val tipoPropiedadVivienda: String = "Propia",
    val permisoPropietarioDetalle: String = "",
    val espaciosDisponiblesMascota: String = "Toda la casa",
    val tieneAlergias: Boolean? = null,
    val alergiasDetalle: String = "",
    val vivesConPersonasEspeciales: Boolean? = null,
    val personasEspecialesDetalle: String = "",
    val motivoAdopcion: String = "",
    val haCuidadoMascotasAntes: Boolean? = null,
    val experienciaPreviaDetalle: String = "",
    val aceptaEsterilizacion: Boolean? = null,
    val aceptaSeguimiento: Boolean? = null,
    val puedeCubrirGastosVeterinarios: Boolean? = null,
    val finalizado: Boolean = false
)

object SolicitudAdopcionValidation {
    fun validate(draft: SolicitudAdopcionDraft, enviar: Boolean): Map<String, String> = buildMap {
        if (enviar) {
            if (draft.nombreCompleto.isBlank()) put("nombreCompleto", "Ingresa tu nombre completo.")
            if (draft.dni.trim().length != 8 || draft.dni.trim().any { !it.isDigit() }) put("dni", "El DNI debe tener 8 dígitos numéricos.")
            if (!draft.dniVerificado) put("dniVerificado", "Debes verificar tu DNI antes de enviar.")
            if (draft.direccionHogar.isBlank()) put("direccionHogar", "Ingresa la dirección de tu hogar.")
            if (draft.telefonoContacto.isBlank()) put("telefonoContacto", "Ingresa tu teléfono de contacto.")
            if (draft.motivoAdopcion.isBlank()) put("motivoAdopcion", "Explica brevemente por qué deseas adoptar.")
            if (draft.tieneAlergias == null) put("tieneAlergias", "Selecciona Sí o No.")
            if (draft.vivesConPersonasEspeciales == null) put("vivesConPersonasEspeciales", "Selecciona Sí o No.")
            if (draft.haCuidadoMascotasAntes == null) put("haCuidadoMascotasAntes", "Selecciona Sí o No.")
            if (draft.aceptaEsterilizacion == null || draft.aceptaEsterilizacion == false) put("aceptaEsterilizacion", "Debes aceptar la esterilización veterinaria.")
            if (draft.aceptaSeguimiento == null || draft.aceptaSeguimiento == false) put("aceptaSeguimiento", "Debes aceptar el seguimiento de adopción.")
            if (draft.puedeCubrirGastosVeterinarios == null || draft.puedeCubrirGastosVeterinarios == false) put("puedeCubrirGastosVeterinarios", "Debes confirmar la capacidad de cubrir gastos veterinarios.")
        }
    }
}
