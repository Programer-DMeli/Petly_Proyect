package com.petly_app.data.repository

import android.content.ContentResolver
import android.net.Uri
import com.petly_app.data.remote.HistoriaAdopcionDto
import com.petly_app.data.remote.HistoriasAdopcionApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.buffer
import okio.source
import java.io.IOException

internal const val MAX_FOTOS_HISTORIA = 5
internal const val MAX_CARACTERES_HISTORIA = 4000
private const val MAX_BYTES_FOTOS = 20 * 1024 * 1024

internal data class HistoriasAdopcion(
    val elegibles: Set<Long>,
    val enviadas: Map<Long, HistoriaAdopcionDto>,
)

class HistoriasAdopcionRepository(
    private val api: HistoriasAdopcionApi,
    private val resolver: ContentResolver,
) {
    internal suspend fun cargar(): HistoriasAdopcion = coroutineScope {
        val disponibles = async { api.adopcionesDisponibles() }
        val enviadas = buildMap {
            var pagina = 1
            do {
                val respuesta = api.listar(pagina++)
                respuesta.results.forEach { put(it.solicitud_id, it) }
            } while (respuesta.next != null)
        }
        HistoriasAdopcion(disponibles.await().map { it.id }.toSet() - enviadas.keys, enviadas)
    }

    internal suspend fun enviar(solicitudId: Long, historia: String, fotos: List<Uri>): HistoriaAdopcionDto {
        val texto = historia.trim()
        require(texto.length in 1..MAX_CARACTERES_HISTORIA) { "Escribe una historia de hasta 4000 caracteres." }
        require(fotos.size in 1..MAX_FOTOS_HISTORIA) { "Selecciona entre 1 y 5 fotos." }
        val partes = withContext(Dispatchers.IO) {
            var restantes = MAX_BYTES_FOTOS
            fotos.mapIndexed { indice, uri ->
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                require(mime in setOf("image/jpeg", "image/png", "application/octet-stream")) {
                    "Selecciona fotos en formato JPEG o PNG."
                }
                val datos = leerFoto(uri, restantes)
                require(datos.isNotEmpty()) { "Una de las fotos seleccionadas está vacía." }
                restantes -= datos.size
                val extension = when (mime) {
                    "image/jpeg" -> ".jpg"
                    "image/png" -> ".png"
                    else -> ""
                }
                MultipartBody.Part.createFormData(
                    "fotos", "historia-${indice + 1}$extension", datos.toRequestBody(mime.toMediaType())
                )
            }
        }
        val textPlain = "text/plain".toMediaType()
        return api.enviar(solicitudId.toString().toRequestBody(textPlain), texto.toRequestBody(textPlain), partes)
    }

    private fun leerFoto(uri: Uri, limite: Int): ByteArray {
        val mensaje = "No se pudo abrir una de las fotos seleccionadas. Vuelve a seleccionarla."
        try {
            val archivo = resolver.openInputStream(uri) ?: throw IllegalArgumentException(mensaje)
            return archivo.source().buffer().use { fuente ->
                require(!fuente.request(limite.toLong() + 1)) { "Las fotos juntas no pueden superar 20 MB." }
                fuente.readByteArray()
            }
        } catch (error: IOException) {
            throw IllegalArgumentException(mensaje, error)
        } catch (error: SecurityException) {
            throw IllegalArgumentException(mensaje, error)
        }
    }
}
