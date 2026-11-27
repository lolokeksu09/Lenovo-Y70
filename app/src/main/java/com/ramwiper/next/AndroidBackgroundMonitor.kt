package com.ramwiper.next

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker
import com.ramwiper.next.data.Monitor
import com.ramwiper.next.data.SettingsStore
import com.ramwiper.next.domain.AutomationPolicy
import com.ramwiper.next.domain.BackgroundMonitor
import java.util.concurrent.TimeUnit

private const val WORK = "ram_memory_alerts_v1"
private const val CHANNEL = "memory_alerts"
private const val NOTIFICATION_ID = 601

class AndroidBackgroundMonitor(context: Context) : BackgroundMonitor {
    private val app = context.applicationContext

    /** Called on the IO dispatcher. One periodic job, never a foreground service. */
    override fun sync(enabled: Boolean) {
        val manager = WorkManager.getInstance(app)
        if (enabled) {
            val request = PeriodicWorkRequestBuilder<MemoryAlertWorker>(15, TimeUnit.MINUTES).build()
            manager.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
                .result.get(10, TimeUnit.SECONDS)
        } else {
            manager.cancelUniqueWork(WORK).result.get(10, TimeUnit.SECONDS)
            app.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
        }
    }
}

/** Samples and alerts only. This class has no route to Rust or the privileged service. */
class MemoryAlertWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): ListenableWorker.Result {
        val app = applicationContext
        val prefs = app.getSharedPreferences("memory", Context.MODE_PRIVATE)
        val store = SettingsStore(prefs)
        return try {
            val settings = store.load().automation
            val manager = requireNotNull(app.getSystemService(NotificationManager::class.java))
            if (!settings.backgroundAlerts) {
                manager.cancel(NOTIFICATION_ID)
                return ListenableWorker.Result.success()
            }
            val snapshot = Monitor(app).read()
            val percent = AutomationPolicy.availablePercent(snapshot)
            // Widget refresh shares the measured snapshot rather than starting an extra allocation.
            MemoryWidgetProvider.updateAll(app, snapshot)
            if (percent == null || !AutomationPolicy.belowThreshold(snapshot, settings) ||
                !store.load().automation.backgroundAlerts || isStopped) {
                manager.cancel(NOTIFICATION_ID)
                return ListenableWorker.Result.success()
            }
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Уведомления о памяти", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.areNotificationsEnabled() &&
                manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE) {
                val open = PendingIntent.getActivity(app, 601,
                    Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val notification = Notification.Builder(app, CHANNEL)
                    .setSmallIcon(R.drawable.ic_memory)
                    .setContentTitle("Доступная RAM: $percent%")
                    .setContentText("Открой RAM Wiper для проверки состояния памяти.")
                    .setContentIntent(open)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
                if (!isStopped && store.load().automation.backgroundAlerts) manager.notify(NOTIFICATION_ID, notification)
            }
            ListenableWorker.Result.success()
        } catch (_: Exception) {
            if (isStopped) ListenableWorker.Result.success() else ListenableWorker.Result.retry()
        }
    }
}
