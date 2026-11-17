package com.petly_app.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.petly_app.BuildConfig
import com.petly_app.MainActivity
import com.petly_app.R
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PushRegistration(
    private val context: Context,
    private val accessToken: (Long) -> String?
) {
    private val prefs = context.getSharedPreferences("push_registration", Context.MODE_PRIVATE)
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private var scheduledRevocation: java.util.concurrent.ScheduledFuture<*>? = null
    private val http = OkHttpClient.Builder().followRedirects(false).callTimeout(15, TimeUnit.SECONDS).build()
    private val notifications = context.getSystemService(NotificationManager::class.java)

    val configured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    @Synchronized
    fun register(owner: Long) {
        if (!configured) return
        if (prefs.getLong("owner", 0) != owner) unlink()
        val key = activeKey() ?: ByteArray(32).also(SecureRandom()::nextBytes).hex().also {
            prefs.edit().putString("active", it).putLong("owner", owner).commit()
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            worker.execute {
                if (activeKey() != key) return@execute
                try {
                    val bearer = accessToken(owner) ?: return@execute
                    if (activeKey() == key) send("PUT", "", key, token, bearer)
                } catch (_: IOException) {
                    // El registro se reintenta al volver a abrir la app.
                }
            }
        }
    }

    fun rotate(token: String) {
        val key = activeKey() ?: return
        worker.execute {
            if (activeKey() == key) send("POST", "rotar/", key, token)
        }
    }

    @Synchronized
    fun unlink() {
        val key = activeKey() ?: return
        prefs.edit().remove("active").remove("owner").putStringSet("revocations", revocations() + key).commit()
        notifications.cancelAll()
        retryRevocations()
    }

    fun retryRevocations() {
        worker.execute {
            scheduledRevocation?.cancel(false)
            for (key in revocations()) {
                if (send("POST", "revocar/", key) == 204) removeRevocation(key)
            }
            if (revocations().isNotEmpty()) {
                scheduledRevocation = worker.schedule({ retryRevocations() }, 60, TimeUnit.SECONDS)
            }
        }
    }

    @Synchronized
    private fun removeRevocation(key: String) {
        prefs.edit().putStringSet("revocations", revocations() - key).commit()
    }

    private fun revocations(): Set<String> = prefs.getStringSet("revocations", emptySet()).orEmpty().toSet()

    private fun activeKey(): String? = prefs.getString("active", null)

    private fun send(method: String, operation: String, key: String, token: String? = null, bearer: String? = null): Int? {
        val body = JSONObject().put("clave", key)
        if (token != null) body.put("token", token)
        val request = Request.Builder()
            .url("${BuildConfig.ADMIN_API_BASE_URL}api/acceso/notificaciones/dispositivos/$operation")
            .method(method, body.toString().toRequestBody("application/json".toMediaType()))
        if (bearer != null) request.header("Authorization", "Bearer $bearer")
        return try {
            http.newCall(request.build()).execute().use { it.code }
        } catch (_: IOException) {
            null
        }
    }

    fun matches(binding: String?): Boolean = binding != null && activeKey()?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() == binding
    } == true

    @Synchronized
    fun show(event: Long, binding: String) {
        if (!matches(binding)) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= 26) {
            notifications.createNotificationChannel(NotificationChannel(
                CHANNEL, "Solicitudes de adopción", NotificationManager.IMPORTANCE_HIGH
            ))
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("petly://aviso/$event")
            putExtra("vinculo", binding)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        val open = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifications.notify(event.toString(), 1, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_petly_notification)
            .setContentTitle("Petly")
            .setContentText("Hay una actualización de tu solicitud de adopción.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val CHANNEL = "solicitudes"
    }
}
