package com.petly_app.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

data class VacunaProgramadaDto(val id: Long, val nombre: String, val fecha_programada: String)
data class DatosVacunaProgramada(val nombre: String, val fecha_programada: String)
data class AgendaVacunasDto(val mascota_nombre: String, val vacunas: List<VacunaProgramadaDto>)

interface AgendaVacunasApi {
    @GET("api/acceso/solicitudes/{solicitud}/vacunas-programadas/")
    suspend fun listar(@Path("solicitud") solicitud: Long): AgendaVacunasDto

    @POST("api/acceso/solicitudes/{solicitud}/vacunas-programadas/")
    suspend fun crear(@Path("solicitud") solicitud: Long, @Body datos: DatosVacunaProgramada): VacunaProgramadaDto

    @PATCH("api/acceso/solicitudes/{solicitud}/vacunas-programadas/{vacuna}/")
    suspend fun editar(
        @Path("solicitud") solicitud: Long, @Path("vacuna") vacuna: Long, @Body datos: DatosVacunaProgramada
    ): VacunaProgramadaDto

    @DELETE("api/acceso/solicitudes/{solicitud}/vacunas-programadas/{vacuna}/")
    suspend fun eliminar(@Path("solicitud") solicitud: Long, @Path("vacuna") vacuna: Long)
}
