package com.petly_app.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.petly_app.data.model.KycDto
import com.petly_app.data.model.dniValido
import com.petly_app.data.remote.KycApi
import com.petly_app.data.remote.KycConsultaApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class KycRepository(private val api: KycApi, private val consultaApi: KycConsultaApi, private val resolver: ContentResolver) {
    suspend fun load(): KycDto? = api.list().results.firstOrNull()

    suspend fun upload(dni: String, nombres: String, apellidos: String, anverso: Uri, reverso: Uri): KycDto {
        fun image(uri: Uri, partName: String): MultipartBody.Part {
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("No se pudo leer la imagen seleccionada.")
            require(bytes.isNotEmpty() && bytes.size <= 5 * 1024 * 1024) { "Cada imagen debe pesar como máximo 5 MB." }
            val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
                ?: throw IllegalArgumentException("Selecciona una imagen válida para el DNI.")
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            return MultipartBody.Part.createFormData(partName, "dni-$partName.$extension", bytes.toRequestBody(mime.toMediaType()))
        }
        require(dniValido(dni)) { "El DNI debe tener exactamente ocho dígitos." }
        return api.upload(
            dni.toRequestBody("text/plain".toMediaType()),
            nombres.trim().toRequestBody("text/plain".toMediaType()),
            apellidos.trim().toRequestBody("text/plain".toMediaType()),
            image(anverso, "documento_anverso"), image(reverso, "documento_reverso")
        )
    }

    suspend fun consultar(id: Long) = consultaApi.consultar(id)
}
