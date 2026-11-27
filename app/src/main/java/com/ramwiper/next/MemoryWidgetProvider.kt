package com.ramwiper.next

import android.app.ActivityManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.ramwiper.next.domain.AutomationPolicy
import com.ramwiper.next.domain.Snapshot
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal fun readBasicMemory(context: Context): Snapshot {
    val am = requireNotNull(context.getSystemService(ActivityManager::class.java))
    val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
    return Snapshot(info.totalMem, info.availMem, info.threshold, info.lowMemory)
}

class MemoryWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids, runCatching { readBasicMemory(context) }.getOrNull())
    }
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == REFRESH) {
            updateAll(context, runCatching { readBasicMemory(context) }.getOrNull())
            return
        }
        super.onReceive(context, intent)
    }
    companion object {
        private const val REFRESH = "com.ramwiper.next.REFRESH_MEMORY_WIDGET"
        fun updateAll(context: Context, snapshot: Snapshot?) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MemoryWidgetProvider::class.java))
            update(context, manager, ids, snapshot)
        }
        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray, snapshot: Snapshot?) {
            if (ids.isEmpty()) return
            val percent = snapshot?.let { AutomationPolicy.availablePercent(it) }
            val open = PendingIntent.getActivity(context, 701,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val refresh = PendingIntent.getBroadcast(context, 702,
                Intent(context, MemoryWidgetProvider::class.java).setAction(REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val views = RemoteViews(context.packageName, R.layout.memory_widget).apply {
                setTextViewText(R.id.widget_available, if (snapshot != null && percent != null)
                    String.format(Locale.getDefault(), "%.1f ГиБ · %d%%", snapshot.available / (1024.0 * 1024 * 1024), percent)
                    else "Нет данных")
                setProgressBar(R.id.widget_progress, 100, percent ?: 0, false)
                setTextViewText(R.id.widget_time, if (percent == null) "Замер недоступен" else
                    "Замер: " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date()))
                setOnClickPendingIntent(R.id.widget_open, open)
                setOnClickPendingIntent(R.id.widget_refresh, refresh)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}
