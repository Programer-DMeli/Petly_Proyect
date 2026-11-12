package com.petly_app.core.session

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Primera versión: sesión en memoria, sin guardar token ni contraseña
class SessionManager {
    var onStart: ((Long, String) -> Unit)? = null
    var onClear: (() -> Unit)? = null
        set(value) = synchronized(this) { field = value }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _authenticated = MutableStateFlow(false)
    val authenticated = _authenticated.asStateFlow()
    private var expirationJob: Job? = null
    private var accountId: Long? = null
    private var accessToken: String? = null
    private var expiresAt = 0L

    @Synchronized
    fun start(token: String, expiresInSeconds: Long, userId: Long) {
        require(token.isNotBlank())
        require(expiresInSeconds in 1..604800) // Contrato: máximo siete días.
        expirationJob?.cancel()
        accessToken = token
        expiresAt = SystemClock.elapsedRealtime() + expiresInSeconds * 1000L
        accountId = userId
        onStart?.invoke(userId, token)
        _authenticated.value = true
        expirationJob = scope.launch {
            delay(expiresInSeconds * 1000L)
            clearIfMatches(token)
        }
    }

    @Synchronized
    fun token(): String? {
        if (accessToken != null && SystemClock.elapsedRealtime() >= expiresAt) clear()
        return accessToken
    }

    @Synchronized
    fun withAccount(action: (Long, String) -> Unit) {
        val token = token() ?: return
        accountId?.let { action(it, token) }
    }

    @Synchronized
    fun clearIfMatches(token: String) {
        if (accessToken == token) clear()
    }

    @Synchronized
    fun clear() {
        expirationJob?.cancel()
        expirationJob = null
        if (accessToken != null) onClear?.invoke()
        accessToken = null
        accountId = null
        expiresAt = 0L
        _authenticated.value = false
    }
}
