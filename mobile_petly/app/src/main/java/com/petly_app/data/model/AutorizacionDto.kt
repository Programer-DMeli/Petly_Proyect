package com.petly_app.data.model
data class AlbergueDto(val id: Long, val nombre: String, val direccion: String)
data class AutorizacionDto(val albergueId: Long, val nombre: String = "", val estado: String = "ACTIVA")
