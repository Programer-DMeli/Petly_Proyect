package com.petly_app.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.webkit.MimeTypeMap
import com.petly_app.data.model.KycDto
import com.petly_app.data.model.dniValido
import com.petly_app.data.remote.KycApi
import com.petly_app.data.remote.KycConsultaApi
import com.petly_app.data.remote.LimiteImagenApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class KycRepository(
    private val api: KycApi,
    private val consultaApi: KycConsultaApi,
    private val resolver: ContentResolver,
    private val configuracion: LimiteImagenApi
) {
    suspend fun load(): KycDto? = api.list().results.firstOrNull()

    suspend fun upload(dni: String, nombres: String, apellidos: String, anverso: Uri, reverso: Uri): KycDto {
        require(dniValido(dni)) { "El DNI debe tener exactamente ocho dígitos." }
        val limiteMb = configuracion.obtenerLimiteImagenMb()
        suspend fun image(uri: Uri, partName: String): MultipartBody.Part = withContext(Dispatchers.IO) {
            val bytes = resolver.leerImagen(uri, limiteMb * BYTES_POR_MIB, "Cada imagen admite hasta $limiteMb MB.")
            val mime = resolver.getType(uri)?.takeIf { it.startsWith("image/") }
                ?: throw IllegalArgumentException("Selecciona una imagen válida para el DNI.")
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
            MultipartBody.Part.createFormData(partName, "dni-$partName.$extension", bytes.toRequestBody(mime.toMediaType()))
        }
        return api.upload(
            dni.toRequestBody("text/plain".toMediaType()),
            nombres.trim().toRequestBody("text/plain".toMediaType()),
            apellidos.trim().toRequestBody("text/plain".toMediaType()),
            image(anverso, "documento_anverso"), image(reverso, "documento_reverso")
        )
    }

    suspend fun consultar(id: Long) = consultaApi.consultar(id)
}
