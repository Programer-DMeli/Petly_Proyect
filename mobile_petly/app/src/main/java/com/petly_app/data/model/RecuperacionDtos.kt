package com.petly_app.data.model
data class RecuperacionRequest(val correo: String)
data class ResetRequest(val token: String, val nuevaPassword: String, val nuevaPasswordConfirm: String)
