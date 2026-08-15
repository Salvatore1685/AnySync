package com.routersync.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.routersync.app.MainActivity
import com.routersync.app.R
import com.routersync.app.data.AppDatabase
import com.routersync.app.data.SyncProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Widget per la home screen: mostra a colpo d'occhio lo stato dell'ultima sincronizzazione fra
 * tutti i profili configurati (quale sync, se riuscita o no, quanto tempo fa). Toccarlo apre
 * l'app. Si aggiorna da solo subito dopo ogni sincronizzazione (vedi la chiamata a
 * [requestUpdate] in [com.routersync.app.work.SyncWorker]), oltre che al ritmo minimo concesso
 * da Android (30 minuti, vedi sync_status_widget_info.xml) come rete di sicurezza.
 */
class SyncStatusWidgetProvider : AppWidgetProvider() {

    companion object {
        /** Da chiamare dopo ogni sync per aggiornare subito il widget, se l'utente ne ha uno sulla home. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, SyncStatusWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, SyncStatusWidgetProvider::class.java).apply {
                        action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                    }
                )
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Le query al database non possono girare sul thread principale che riceve questo callback.
        CoroutineScope(Dispatchers.IO).launch {
            val profiles = runCatching { AppDatabase.getInstance(context).syncProfileDao().getAllOnce() }.getOrDefault(emptyList())
            val views = buildViews(context, profiles)
            appWidgetIds.forEach { id -> appWidgetManager.updateAppWidget(id, views) }
        }
    }

    private fun buildViews(context: Context, profiles: List<SyncProfile>): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_sync_status)

        val openAppIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

        if (profiles.isEmpty()) {
            views.setTextViewText(R.id.widget_status, "Nessuna sync configurata")
            views.setTextViewText(R.id.widget_subtitle, "Tocca per aprire AnySync")
            return views
        }

        val mostRecent = profiles.filter { it.lastSyncTimestamp != null }.maxByOrNull { it.lastSyncTimestamp!! }
        if (mostRecent == null) {
            views.setTextViewText(R.id.widget_status, "Nessuna sync eseguita ancora")
            views.setTextViewText(R.id.widget_subtitle, "${profiles.size} sync configurate")
            return views
        }

        val status = mostRecent.lastSyncStatus.orEmpty()
        val (icon, label, colorHex) = when {
            status.startsWith("OK") -> Triple("✓", "Aggiornato", "#34D399")
            status.startsWith("Interrotta") -> Triple("⏸", "Interrotta", "#FBBF24")
            else -> Triple("⚠", "Errore", "#F87171")
        }
        views.setTextViewText(R.id.widget_status, "$icon ${mostRecent.name}")
        views.setTextColor(R.id.widget_status, android.graphics.Color.parseColor(colorHex))
        views.setTextViewText(R.id.widget_subtitle, "$label · ${formatTimeAgo(mostRecent.lastSyncTimestamp!!)}")

        return views
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - timestamp)
        return when {
            minutes < 1 -> "adesso"
            minutes < 60 -> "$minutes min fa"
            minutes < 60 * 24 -> "${minutes / 60} h fa"
            else -> "${minutes / (60 * 24)} g fa"
        }
    }
}
