package com.petly_app.data.model

data class KycDto(
    val id: Long,
    val dni: String,
    val nombres: String,
    val apellidos: String,
    val estado: String,
    val coincide: Boolean? = null,
    val proveedor: String = "",
    val referencia_reniec: String = "",
    val fecha_consulta: String? = null,
    val error_servicio: String = ""
)

data class KycConsultaDto(
    val id: Long,
    val estado: String,
    val coincide: Boolean? = null,
    val referencia: String = "",
    val fechaConsulta: String = ""
)

data class KycPageDto(val results: List<KycDto> = emptyList())
