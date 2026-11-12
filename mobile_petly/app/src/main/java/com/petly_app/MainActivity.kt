package com.petly_app

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.petly_app.core.navigation.PetlyNavHost
import com.petly_app.ui.theme.Petly_appTheme

class MainActivity : ComponentActivity() {
    private val resetToken = mutableStateOf<String?>(null)
    private val pushEvent = mutableStateOf<Long?>(null)
    private val app get() = application as PetlyApplication
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private fun acceptLink(intent: Intent?) {
        val uri = intent?.data
        if(uri?.scheme == "petly" && uri.host == "recuperacion") {
            app.container.session.clear()
            app.push.unlink()
            pushEvent.value = null
            resetToken.value = uri.getQueryParameter("token")
        } else if (uri?.scheme == "petly" && uri.host == "aviso" &&
            app.push.matches(intent.getStringExtra("vinculo"))
        ) {
            pushEvent.value = uri.lastPathSegment?.toLongOrNull()?.takeIf { it > 0 }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptLink(intent) }


    override fun onResume() {
        super.onResume()
        app.container.session.withAccount(app.push::register)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        acceptLink(intent)
        enableEdgeToEdge()

        val container = app.container
        setContent {
            val authenticated = container.session.authenticated.collectAsStateWithLifecycle()
            LaunchedEffect(authenticated.value) {
                val permissions = getPreferences(MODE_PRIVATE)
                if (authenticated.value && app.push.configured && Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !permissions.getBoolean("asked_for_notifications", false)
                ) {
                    permissions.edit().putBoolean("asked_for_notifications", true).apply()
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            Petly_appTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                ) {
                    PetlyNavHost(
                        container, resetToken.value, pushEvent.value,
                        onPushHandled = { pushEvent.value = null; intent.data = null },
                        onResetHandled = { resetToken.value = null; intent.data = null }
                    )
                }
            }
        }
    }
}
