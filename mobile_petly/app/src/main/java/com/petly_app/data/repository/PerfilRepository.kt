package com.petly_app.data.repository

import com.petly_app.data.model.PerfilDraft

interface PerfilRepository {
    suspend fun load(): PerfilDraft?
    suspend fun save(draft: PerfilDraft)
}
