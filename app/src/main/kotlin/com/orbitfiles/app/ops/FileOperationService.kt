package com.orbitfiles.app.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.orbitfiles.app.MainActivity
import com.orbitfiles.app.OrbitApplication
import com.orbitfiles.app.R
import com.orbitfiles.core.Formatters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps long transfers alive in the background and shows their progress in the shade. */
class FileOperationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Operações de arquivos", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progresso de cópias, movimentações e compactações"
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, build("Preparando…", null, 0), type)
        } catch (_: Exception) {
            stopSelf(); return START_NOT_STICKY
        }
        if (watcher == null) {
            val ops = (application as OrbitApplication).container.ops
            watcher = scope.launch {
                ops.tasks.collectLatest { tasks ->
                    val running = tasks.filter { it.status == OpStatus.RUNNING }
                    if (running.isEmpty()) {
                        delay(600)
                        ServiceCompat.stopForeground(this@FileOperationService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        val t = running.first()
                        val p = t.progress
                        val pct = ((p?.fraction ?: 0f) * 100).toInt()
                        val detail = buildString {
                            if (p != null && p.totalBytes > 0) append("${Formatters.size(p.doneBytes)} de ${Formatters.size(p.totalBytes)}")
                            if (t.speed > 0) append(" · ${Formatters.speed(t.speed)}")
                            if (running.size > 1) append(" · +${running.size - 1} na fila")
                        }
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, build(t.title, detail.ifBlank { p?.currentName }, pct))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun build(title: String, text: String?, percent: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .setProgress(100, percent, percent == 0)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL = "orbit_ops"
        const val NOTIFICATION_ID = 4242
    }
}
