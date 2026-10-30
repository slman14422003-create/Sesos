package com.sesos.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SesosApp : Application() {
    // one app-wide scope on its own background threads; never touches the main thread
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CookieStore.init(this)
        appScope.launch(Dispatchers.IO) {
            Engine.init(this@SesosApp)
            // refresh yt-dlp quietly in the background so site changes never break downloads
            if (Engine.state.value is Engine.State.Ready) Engine.autoUpdate(this@SesosApp)
        }
    }
}
