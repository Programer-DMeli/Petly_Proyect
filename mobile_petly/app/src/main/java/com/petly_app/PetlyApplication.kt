package com.petly_app

import android.app.Application
import com.petly_app.core.AppContainer

class PetlyApplication : Application() {
    val container by lazy { AppContainer() }
}
