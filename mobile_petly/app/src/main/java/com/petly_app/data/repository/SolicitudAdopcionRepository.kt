package com.petly_app.data.repository

import com.petly_app.data.model.SolicitudAdopcionDraft
import com.petly_app.data.model.SolicitudAdopcionValidation
import com.petly_app.data.remote.*
import com.google.gson.Gson
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SolicitudAdopcionManager {
    private var generation = 0
    var api: SolicitudesApi? = null
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

    var lastId: Long? = null
    fun clear() { generation++; errores.value = ""; draftMap.clear(); _submitted.value = emptyList(); api = null; fotosApi = null; lastId = null; ids.clear(); estados.clear(); fotosLocales.clear() }
    suspend fun cargar() {
        val epoch = generation
        val service = api ?: return
        val rows = mutableListOf<com.google.gson.JsonElement>()
        var page = 1
        do {
            val response = service.listar(page++)
            if (epoch != generation) return
            rows.addAll(if (response.isJsonArray) response.asJsonArray.toList() else response.asJsonObject.getAsJsonArray("results").toList())
            val more = !response.isJsonArray && response.asJsonObject.get("next")?.let { !it.isJsonNull } == true
        } while (more)
        ids.clear(); estados.clear()
        draftMap.entries.removeAll { it.value.finalizado }
        _submitted.value = rows.map { Gson().fromJson(it, SolicitudPersistida::class.java) }
            .filter { it.estado in listOf("PENDIENTE", "OBSERVADA", "APROBADA") }
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
            lastId = ids[draft.mascotaId] ?: service.crear(CrearSolicitud(draft.mascotaId, draft.motivoAdopcion, draft.copy(dniVerificado = false))).id.also { ids[draft.mascotaId] = it }
            if (epoch != generation) return mapOf("servidor" to "La sesión cambió. Vuelve a entrar.")
            for (uri in fotosLocales[draft.mascotaId].orEmpty()) {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    resolver!!.openInputStream(uri)!!.use { it.readNBytes(5 * 1024 * 1024 + 1) }
                }
                require(bytes.size <= 5 * 1024 * 1024) { "Cada foto admite hasta 5 MB." }
                val mime = resolver!!.getType(uri)
                require(mime in listOf("image/jpeg", "image/png")) { "Solo se admiten JPEG y PNG." }
                val body = bytes.toRequestBody(mime!!.toMediaType())
                fotosApi!!.subir(lastId!!, okhttp3.MultipartBody.Part.createFormData("archivo", if(mime == "image/png") "hogar.png" else "hogar.jpg", body))
            }
        } catch (e: Exception) {
            val detail = if (e is retrofit2.HttpException) e.response()?.errorBody()?.string() else null
            return mapOf("servidor" to (detail ?: "No se pudo enviar. Comprueba tu conexión y reintenta."))
        }
        if (epoch != generation) return mapOf("servidor" to "La sesión cambió.")
        val finalDraft = draft.copy(finalizado = true)
        draftMap[draft.mascotaId] = finalDraft
        _submitted.value = _submitted.value.filter { it.mascotaId != draft.mascotaId } + finalDraft
        return emptyMap()
    }
}
