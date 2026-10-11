package com.petly_app.core.network

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException
import com.petly_app.data.repository.BackendPendingException

class ContractException : Exception()
class AdoptanteOnlyException : Exception()

fun apiErrorMessage(error: Exception): String {
    if (error is CancellationException) throw error
    return when (error) {
        is BackendPendingException -> "Esta función todavía no está conectada al servidor."
        is IOException -> "No se pudo conectar al servidor. Revisa que Spring Boot esté ejecutándose (http://10.0.2.2:8080 en emulador)."
        is ContractException -> "La respuesta del servidor no coincide con la estructura esperada de la API."
        is AdoptanteOnlyException -> "Esta aplicación es para cuentas de adoptantes."
        is HttpException -> when (error.code()) {
            400, 422 -> "Revisa los datos ingresados."
            401 -> "Credenciales inválidas o sesión vencida."
            403 -> "Tu cuenta no tiene permiso para esta operación."
            404 -> "El servicio no existe o la ruta no está configurada en Spring Boot."
            409 -> "El correo ya está registrado."
            423 -> "Cuenta bloqueada temporalmente. Intenta después de 5 minutos."
            429 -> "Demasiados intentos. Espera antes de volver a intentar."
            in 500..599 -> "El servidor de Spring Boot tuvo un problema interno (${error.code()})."
            else -> "La operación no pudo completarse (${error.code()})."
        }
        else -> "No se pudo procesar la respuesta. Revisa la configuración del servidor."
    }
}

