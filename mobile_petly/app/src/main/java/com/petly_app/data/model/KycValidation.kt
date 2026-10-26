package com.petly_app.data.model

fun dniValido(dni: String): Boolean = dni.matches(Regex("[0-9]{8}"))
