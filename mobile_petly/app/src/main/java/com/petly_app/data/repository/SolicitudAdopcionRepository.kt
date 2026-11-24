package com.petly_app.data.repository

import com.petly_app.data.model.SolicitudAdopcionDraft
import com.petly_app.data.model.SolicitudAdopcionValidation
import com.petly_app.data.remote.*
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

object SolicitudAdopcionManager {
    private var generation = 0
    var api: SolicitudesApi? = null
    var resenasApi: ResenasApi? = null
    var fichaVeterinariaApi: FichaVeterinariaApi? = null
    var fotosApi: FotosHogarApi? = null
    var resolver: android.content.ContentResolver? = null
    val fotosLocales = mutableMapOf<Long, List<android.net.Uri>>()
    private val estados = mutableMapOf<Long, String>()
    fun identificador(mascotaId: Long) = ids[mascotaId]
    fun estado(mascotaId: Long) = estados[mascotaId] ?: "PENDIENTE"
    private val ids = mutableMapOf<Long, Long>()
    private val errores = MutableStateFlow("")
    val error = errores.asStateFlow()
    suspend fun fotosGuardadas(mascotaId: Long): List<FotoHogarDto> = ids[mascotaId]?.let { fotosApi?.listar(it) } ?: emptyList()

    fun clear() { generation++; errores.value = ""; draftMap.clear(); _submitted.value = emptyList(); api = null; fotosApi = null; fichaVeterinariaApi = null; resenasApi = null; ids.clear(); estados.clear(); fotosLocales.clear() }

    suspend fun listarSolicitudes(): List<SolicitudPersistida> {
        val service = checkNotNull(api)
        val gson = Gson()
        val rows = mutableListOf<SolicitudPersistida>()
        var page = 1
        do {
            val response = service.listar(page++)
            val results = if (response.isJsonArray) response.asJsonArray else response.asJsonObject.getAsJsonArray("results")
            results.mapTo(rows) { gson.fromJson(it, SolicitudPersistida::class.java) }
            val more = !response.isJsonArray && response.asJsonObject.get("next")?.let { !it.isJsonNull } == true
        } while (more)
        return rows
    }

    suspend fun cargar() {
        val epoch = generation
        if (api == null) return
        val rows = listarSolicitudes()
        if (epoch != generation) return
        ids.clear(); estados.clear()
        draftMap.entries.removeAll { it.value.finalizado }
        _submitted.value = rows.filter { it.estado in listOf("PENDIENTE", "OBSERVADA", "APROBADA") }
            .onEach { ids[it.mascota] = it.id; estados[it.mascota] = it.estado }
            .map {
                val defaults = Gson().toJsonTree(SolicitudAdopcionDraft()).asJsonObject
                it.respuestas_cuestionario?.let { saved -> Gson().toJsonTree(saved).asJsonObject.entrySet().forEach { (key, value) -> if (!value.isJsonNull) defaults.add(key, value) } }
                Gson().fromJson(defaults, SolicitudAdopcionDraft::class.java).copy(mascotaId = it.mascota, mascotaNombre = it.mascota_nombre ?: "", finalizado = true)
            }
            .onEach { draftMap[it.mascotaId] = it }
    }

    private var draftMap = mutableMapOf<Long, SolicitudAdopcionDraft>()
    private val _submitted = MutableStateFlow<List<SolicitudAdopcionDraft>>(emptyList())
    val submitted: StateFlow<List<SolicitudAdopcionDraft>> = _submitted.asStateFlow()
    fun solicitudesActivas(): List<Triple<Long, Long, String>> = _submitted.value.mapNotNull { draft ->
        ids[draft.mascotaId]?.let { Triple(it, draft.mascotaId, draft.mascotaNombre) }
    }

    fun getDraft(mascotaId: Long, mascotaNombre: String): SolicitudAdopcionDraft {
        return draftMap[mascotaId] ?: SolicitudAdopcionDraft(mascotaId = mascotaId, mascotaNombre = mascotaNombre)
    }

    fun saveDraft(draft: SolicitudAdopcionDraft) {
        draftMap[draft.mascotaId] = draft
    }

    fun isSolicitudEnviada(mascotaId: Long): Boolean {
        return _submitted.value.any { it.mascotaId == mascotaId && it.finalizado } || draftMap[mascotaId]?.finalizado == true
    }

    suspend fun enviarSolicitud(draft: SolicitudAdopcionDraft): Map<String, String> {
        val errors = if (ids.containsKey(draft.mascotaId)) emptyMap() else SolicitudAdopcionValidation.validate(draft, enviar = true)
        if (errors.isNotEmpty()) return errors

        val epoch = generation
        val service = api ?: return mapOf("servidor" to "Inicia sesión para enviar una solicitud real.")
        try {
            val solicitudId = ids[draft.mascotaId] ?: service.crear(
                CrearSolicitud(draft.mascotaId, draft.motivoAdopcion, draft.copy(dniVerificado = false))
            ).id
            if (epoch != generation) return mapOf("servidor" to "La sesión cambió. Vuelve a entrar.")
            ids[draft.mascotaId] = solicitudId
            val fotos = fotosLocales[draft.mascotaId].orEmpty()
            if (fotos.isNotEmpty()) {
                val apiFotos = checkNotNull(fotosApi)
                val contenido = checkNotNull(resolver)
                val limiteMb = service.obtenerLimiteImagenMb()
                for (uri in fotos) {
                    if (epoch != generation) return mapOf("servidor" to "La sesión cambió. Vuelve a entrar.")
                    val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        contenido.leerImagen(uri, limiteMb * BYTES_POR_MIB, "Cada foto admite hasta $limiteMb MB.")
                    }
                    val mime = contenido.getType(uri)
                    if (mime != "image/jpeg" && mime != "image/png") {
                        return mapOf("servidor" to "Solo se admiten JPEG y PNG.")
                    }
                    val body = bytes.toRequestBody(mime.toMediaType())
                    val nombre = if (mime == "image/png") "hogar.png" else "hogar.jpg"
                    apiFotos.subir(solicitudId, okhttp3.MultipartBody.Part.createFormData("archivo", nombre, body))
                    if (epoch != generation) return mapOf("servidor" to "La sesión cambió.")
                    fotosLocales[draft.mascotaId] = fotosLocales[draft.mascotaId].orEmpty() - uri
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is ImagenCargaInvalida) return mapOf("servidor" to e.message.orEmpty())
            if (e is HttpException) return erroresSolicitud(e)
            return mapOf("servidor" to "No se pudo completar el envío. Comprueba tu conexión y reintenta.")
        }
        if (epoch != generation) return mapOf("servidor" to "La sesión cambió.")
        val finalDraft = draft.copy(finalizado = true)
        draftMap[draft.mascotaId] = finalDraft
        _submitted.value = _submitted.value.filter { it.mascotaId != draft.mascotaId } + finalDraft
        return emptyMap()
    }
}

private fun erroresSolicitud(error: HttpException): Map<String, String> {
    val fields = runCatching {
        JsonParser.parseString(error.response()?.errorBody()?.string()).asJsonObject.entrySet()
            .mapNotNull { (field, value) ->
                val messages = if (value.isJsonArray) value.asJsonArray.toList() else listOf(value)
                messages.mapNotNull(JsonElement::mensaje).joinToString(" ")
                    .takeIf(String::isNotBlank)?.let { field to it }
            }.toMap()
    }.getOrDefault(emptyMap())
    val message = fields["autorizacion"] ?: fields["perfil"] ?: fields["detail"]
        ?: fields.values.firstOrNull() ?: "No se pudo completar el envío (${error.code()})."
    return fields + ("servidor" to message)
}

private fun JsonElement.mensaje(): String? =
    if (isJsonPrimitive && asJsonPrimitive.isString) asString else null
