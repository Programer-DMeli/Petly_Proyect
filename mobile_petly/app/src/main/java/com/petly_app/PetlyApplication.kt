package com.petly_app

import android.app.Application
import com.petly_app.core.AppContainer
import com.petly_app.core.push.PushRegistration

class PetlyApplication : Application() {
    val container by lazy { AppContainer(applicationContext.contentResolver) }
    val push by lazy { PushRegistration(this) }

    override fun onCreate() {
        super.onCreate()
        container.session.onStart = push::register
        container.session.onClear = { push.unlink() }
        push.retryRevocations()
    }
}
