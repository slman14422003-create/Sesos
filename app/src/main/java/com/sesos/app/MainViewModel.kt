package com.sesos.app

import android.app.Application
import com.sesos.app.R
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var url by mutableStateOf("")
    var loading by mutableStateOf(false)
    var info by mutableStateOf<MediaInfo?>(null)
    var error by mutableStateOf<String?>(null)
    var message by mutableStateOf<String?>(null)
    var downloadAll by mutableStateOf(false)
    var popupStartedAt by mutableStateOf(0L)

    private fun str(id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    fun onSharedUrl(shared: String) {
        url = shared
        popupStartedAt = 0L
        analyze()
    }

    fun analyze() {
        val u = UrlUtils.extract(url) ?: run { error = str(R.string.invalid_url); return }
        url = u
        loading = true; error = null; info = null
        viewModelScope.launch {
            try {
                info = Engine.analyze(u)
            } catch (t: Throwable) {
                val m = Engine.friendly(t)
                val needLogin = Regex("login|cookies|rate-limit|sign in", RegexOption.IGNORE_CASE).containsMatchIn(m)
                error = str(R.string.analyze_failed, m.take(200)) +
                    if (needLogin) "\n\n" + str(R.string.need_login_hint) else ""
            } finally {
                loading = false
            }
        }
    }

    fun download(kind: Kind, height: Int? = null) {
        val i = info ?: return
        DownloadService.start(getApplication(), i, kind, height, downloadAll)
        message = str(R.string.download_started)
    }

    fun updateEngine() {
        message = str(R.string.updating_engine)
        viewModelScope.launch {
            message = try {
                when (Engine.updateYtDlp(getApplication())) {
                    "DONE" -> str(R.string.engine_updated)
                    "ALREADY_UP_TO_DATE" -> str(R.string.engine_up_to_date)
                    else -> str(R.string.engine_done)
                }
            } catch (t: Throwable) {
                str(R.string.update_failed, (t.message ?: "").take(120))
            }
        }
    }
}
