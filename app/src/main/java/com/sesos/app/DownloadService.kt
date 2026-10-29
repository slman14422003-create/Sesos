package com.sesos.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY
        ensureChannel()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification(getString(R.string.notif_downloading)),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )

        val id = intent.getStringExtra(EX_ID) ?: return START_NOT_STICKY
        val kind = Kind.valueOf(intent.getStringExtra(EX_KIND) ?: "VIDEO")
        val height = intent.getIntExtra(EX_HEIGHT, 0).takeIf { it > 0 }
        val all = intent.getBooleanExtra(EX_ALL, false)
        val info = MediaInfo(
            pageUrl = intent.getStringExtra(EX_URL) ?: return START_NOT_STICKY,
            title = intent.getStringExtra(EX_TITLE) ?: "Sesos",
            thumbnail = null, uploader = null, duration = 0, heights = emptyList(),
            hasVideo = false, hasAudio = false, imageUrl = intent.getStringExtra(EX_IMAGE)
        )

        active.incrementAndGet()
        DownloadCenter.add(DownloadItem(id, info.title, kind))
        scope.launch {
            val tmp = File(cacheDir, "dl/$id")
            try {
                val files = Engine.download(id, info, kind, height, all, tmp) { p ->
                    DownloadCenter.update(id) { it.copy(progress = p) }
                }
                var last: Pair<android.net.Uri, String>? = null
                for (f in files) last = MediaSaver.save(this@DownloadService, f, kind)
                DownloadCenter.update(id) {
                    it.copy(status = Status.DONE, progress = 1f, uri = last?.first?.toString(), mime = last?.second)
                }
            } catch (t: Throwable) {
                if (DownloadCenter.canceled.remove(id)) {
                    DownloadCenter.update(id) { it.copy(status = Status.CANCELED) }
                } else {
                    DownloadCenter.update(id) {
                        it.copy(status = Status.ERROR, message = (t.message ?: t.javaClass.simpleName).let { m -> if (m.length > 600) "..." + m.takeLast(600) else m })
                    }
                }
            } finally {
                tmp.deleteRecursively()
                if (active.decrementAndGet() == 0) {
                    ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Sesos")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    companion object {
        private const val CHANNEL = "sesos_downloads"
        private const val NOTIF_ID = 1001
        const val EX_ID = "id"
        const val EX_URL = "url"
        const val EX_TITLE = "title"
        const val EX_KIND = "kind"
        const val EX_HEIGHT = "height"
        const val EX_ALL = "all"
        const val EX_IMAGE = "image"

        fun start(ctx: Context, info: MediaInfo, kind: Kind, height: Int?, all: Boolean) {
            val i = Intent(ctx, DownloadService::class.java)
                .putExtra(EX_ID, System.currentTimeMillis().toString() + "-" + kind.name)
                .putExtra(EX_URL, info.pageUrl)
                .putExtra(EX_TITLE, info.title)
                .putExtra(EX_KIND, kind.name)
                .putExtra(EX_HEIGHT, height ?: 0)
                .putExtra(EX_ALL, all)
                .putExtra(EX_IMAGE, info.imageUrl)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun cancel(id: String) {
            DownloadCenter.canceled.add(id)
            try { YoutubeDL.getInstance().destroyProcessById(id) } catch (_: Throwable) {}
        }
    }
}
