package com.sesos.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SesosApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CookieStore.init(this)
        CoroutineScope(Dispatchers.IO).launch { Engine.init(this@SesosApp) }
    }
}
