package com.petly_app.core.session

import com.petly_app.data.model.LoginResponse
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import retrofit2.HttpException

enum class SessionRestore { LOADING, RETRY, READY }

class SessionExpired : IOException("Tu sesión venció o fue revocada. Inicia sesión nuevamente.")
class SessionUnavailable : IOException("No se pudo actualizar la sesión. Comprueba tu conexión e intenta nuevamente.")

class SessionManager(
    private val store: RefreshTokenStore? = null,
    private val renew: ((RefreshCredential) -> LoginResponse)? = null,
    private val revoke: ((RefreshCredential) -> Unit)? = null,
    private val now: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    var onStart: ((Long) -> Unit)? = null
    var onClear: (() -> Unit)? = null

    private val gate = Any()
    private val _authenticated = MutableStateFlow(false)
    private val _restore = MutableStateFlow(if (store == null) SessionRestore.READY else SessionRestore.LOADING)
    private val _notice = MutableStateFlow<String?>(null)
    val authenticated = _authenticated.asStateFlow()
    val restoreState = _restore.asStateFlow()
    val notice = _notice.asStateFlow()

    internal data class Access(val token: String, val expires: Long, val account: Long, val generation: Long)
    private var generation = 0L
    @Volatile private var access: Access? = null
    private var credential: RefreshCredential? = null
    private var retryAfter = Long.MIN_VALUE

    // Las APIs Spring y Django usan el mismo coordinador. El transporte de renovación
    // es síncrono y sin el interceptor privado para evitar bloquear el dispatcher de OkHttp.
    internal fun accessForRequest(rejected: Access? = null): Access? {
        val previous = access
        if (rejected != null && rejected.generation != previous?.generation) throw SessionExpired()
        if (usable(previous, rejected)) return previous
        return synchronized(gate) {
            val current = access
            if (previous != null && previous.generation != current?.generation) throw SessionExpired()
            if (current == null || usable(current, rejected)) return@synchronized current
            if (credential == null || renew == null) {
                if (rejected != null || now() >= current.expires) {
                    clearLocked(SessionExpired().message)
                    throw SessionExpired()
                }
                return@synchronized current
            }
            refreshLocked()
            access
        }
    }

    suspend fun restore() = withContext(Dispatchers.IO) {
        synchronized(gate) {
            if (_restore.value == SessionRestore.READY) return@synchronized
            _restore.value = SessionRestore.LOADING
            retryAfter = Long.MIN_VALUE
            try {
                credential = store?.read()
                if (credential == null) {
                    _restore.value = SessionRestore.READY
                } else {
                    refreshLocked()
                }
            } catch (_: SessionExpired) {
                _restore.value = SessionRestore.READY
            } catch (_: Exception) {
                _restore.value = SessionRestore.RETRY
            }
        }
    }

    suspend fun login(token: String, expiresInSeconds: Long, userId: Long, refresh: String?) =
        withContext(Dispatchers.IO) { start(token, expiresInSeconds, userId, refresh) }

    fun start(token: String, expiresInSeconds: Long, userId: Long, refresh: String? = null) {
        synchronized(gate) {
            val saved = refresh?.let(::RefreshCredential)
            store?.write(saved)
            credential = saved
            retryAfter = Long.MIN_VALUE
            generation++
            install(token, expiresInSeconds, userId)
        }
    }

    fun token(): String? = access?.takeIf { now() < it.expires }?.token

    fun withAccount(action: (Long) -> Unit) {
        access?.account?.let(action)
    }

    internal fun tokenForAccount(owner: Long): String? =
        accessForRequest()?.takeIf { it.account == owner }?.token

    suspend fun logout() = withContext(Dispatchers.IO) {
        synchronized(gate) {
            val saved = credential ?: store?.read()
            if (saved != null) revoke?.invoke(saved)
            clearLocked(null)
        }
    }

    fun clear() {
        synchronized(gate) { clearLocked(null) }
    }

    private fun usable(value: Access?, rejected: Access?) =
        value != null && value.token != rejected?.token && value.expires - now() > 60_000

    private fun refreshLocked() {
        if (now() < retryAfter) throw SessionUnavailable()
        val saved = checkNotNull(credential)
        val attempt = saved.copy(requestId = saved.requestId ?: UUID.randomUUID().toString())
        try {
            if (attempt != saved) store?.write(attempt)
            credential = attempt
            val response = checkNotNull(renew).invoke(attempt)
            val token = response.getEffectiveToken()?.takeIf(String::isNotBlank) ?: throw SessionUnavailable()
            val newRefresh = response.refreshToken?.takeIf(String::isNotBlank) ?: throw SessionUnavailable()
            val user = response.user ?: throw SessionUnavailable()
            val account = user.id ?: throw SessionUnavailable()
            if (user.getEffectiveRole() != "ADOPTANTE" || access?.account?.let { it != account } == true) {
                clearLocked(SessionExpired().message)
                throw SessionExpired()
            }
            val rotated = RefreshCredential(newRefresh)
            store?.write(rotated)
            credential = rotated
            install(token, response.getEffectiveExpiresIn(), account)
        } catch (error: Exception) {
            if (error is SessionExpired) throw error
            if (error is HttpException && error.code() == 401) {
                clearLocked(SessionExpired().message)
                throw SessionExpired()
            }
            retryAfter = now() + 2_000
            throw SessionUnavailable()
        }
    }

    private fun install(token: String, seconds: Long, userId: Long) {
        require(token.isNotBlank() && seconds in 1..604800)
        val firstAccess = access == null
        access = Access(token, now() + seconds * 1000, userId, generation)
        _notice.value = null
        _authenticated.value = true
        _restore.value = SessionRestore.READY
        if (firstAccess) onStart?.invoke(userId)
    }

    private fun clearLocked(message: String?) {
        try {
            store?.write(null)
        } finally {
            val hadAccess = access != null
            access = null
            credential = null
            retryAfter = Long.MIN_VALUE
            _notice.value = message
            _authenticated.value = false
            _restore.value = SessionRestore.READY
            if (hadAccess) onClear?.invoke()
        }
    }
}
