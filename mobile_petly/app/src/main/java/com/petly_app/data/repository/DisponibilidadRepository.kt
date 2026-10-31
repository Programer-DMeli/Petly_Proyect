package com.petly_app.data.repository

import com.petly_app.data.model.FranjaHoraria
import com.petly_app.data.remote.DisponibilidadApi
import kotlinx.coroutines.delay

interface DisponibilidadRepository {
    suspend fun list(): List<FranjaHoraria>
    suspend fun save(franjas: List<FranjaHoraria>): List<FranjaHoraria>
}

class RemoteDisponibilidadRepository(private val api: DisponibilidadApi) : DisponibilidadRepository {
    override suspend fun list(): List<FranjaHoraria> = api.list()
    override suspend fun save(franjas: List<FranjaHoraria>): List<FranjaHoraria> = api.save(franjas)
}

class PreviewDisponibilidadRepository : DisponibilidadRepository {
    private var franjas = mutableListOf(
        FranjaHoraria(1L, "Lunes", "10:00", "13:00", "Disponible para entrevistas virtuales"),
        FranjaHoraria(2L, "Sábado", "15:00", "18:00", "Disponible para recibir visitas en casa")
    )

    override suspend fun list(): List<FranjaHoraria> {
        delay(200)
        return franjas.toList()
    }

    override suspend fun save(franjas: List<FranjaHoraria>): List<FranjaHoraria> {
        delay(200)
        this.franjas = franjas.toMutableList()
        return this.franjas.toList()
    }
}
