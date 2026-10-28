package com.sesos.app

import android.app.Application
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

    fun onSharedUrl(shared: String) {
        url = shared
        analyze()
    }

    fun analyze() {
        val u = UrlUtils.extract(url) ?: run { error = "الرابط غير صالح"; return }
        url = u
        loading = true; error = null; info = null
        viewModelScope.launch {
            try {
                info = Engine.analyze(u)
            } catch (t: Throwable) {
                val m = (t.message ?: "")
                val needLogin = Regex("login|cookies|rate-limit|sign in", RegexOption.IGNORE_CASE).containsMatchIn(m)
                error = "تعذّر تحليل الرابط: " + m.take(200) +
                    if (needLogin) "\n\nجرّب قسم «الحسابات» أدناه وسجّل دخولك لهذا الموقع." else ""
            } finally {
                loading = false
            }
        }
    }

    fun download(kind: Kind, height: Int? = null) {
        val i = info ?: return
        DownloadService.start(getApplication(), i, kind, height, downloadAll)
        message = "بدأ التحميل"
    }

    fun updateEngine() {
        message = "جارٍ تحديث المحرك…"
        viewModelScope.launch {
            message = try {
                when (Engine.updateYtDlp(getApplication())) {
                    "DONE" -> "تم تحديث yt-dlp"
                    "ALREADY_UP_TO_DATE" -> "المحرك محدّث بالفعل"
                    else -> "تم"
                }
            } catch (t: Throwable) {
                "فشل التحديث: " + (t.message ?: "").take(120)
            }
        }
    }
}
