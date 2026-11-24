package com.petly_app.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.petly_app.data.remote.LimiteImagenApi
import java.io.ByteArrayOutputStream

internal const val BYTES_POR_MIB = 1024 * 1024

internal suspend fun LimiteImagenApi.obtenerLimiteImagenMb(): Int =
    limiteImagen().maxImagenMb.also { check(it in 1..20) }

internal class ImagenCargaInvalida(mensaje: String) : IllegalArgumentException(mensaje)

internal fun ContentResolver.leerImagen(uri: Uri, limite: Int, mensajeLimite: String): ByteArray =
    openInputStream(uri)?.use { input ->
        val buffer = ByteArray(8192)
        val output = ByteArrayOutputStream()
        while (true) {
            val leidos = input.read(buffer)
            if (leidos == -1) break
            if (leidos > limite - output.size()) throw ImagenCargaInvalida(mensajeLimite)
            output.write(buffer, 0, leidos)
        }
        if (output.size() == 0) throw ImagenCargaInvalida("La imagen seleccionada está vacía.")
        output.toByteArray()
    } ?: throw ImagenCargaInvalida("No se pudo abrir la imagen seleccionada.")
