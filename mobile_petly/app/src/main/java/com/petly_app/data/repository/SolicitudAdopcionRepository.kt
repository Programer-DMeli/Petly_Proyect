package com.petly_app.data.repository

import com.petly_app.data.model.SolicitudAdopcionDraft
import com.petly_app.data.model.SolicitudAdopcionValidation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SolicitudAdopcionManager {
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
        delay(300)
        val errors = SolicitudAdopcionValidation.validate(draft, enviar = true)
        if (errors.isNotEmpty()) return errors

        val finalDraft = draft.copy(finalizado = true)
        draftMap[draft.mascotaId] = finalDraft
        _submitted.value = _submitted.value.filter { it.mascotaId != draft.mascotaId } + finalDraft
        return emptyMap()
    }
}
