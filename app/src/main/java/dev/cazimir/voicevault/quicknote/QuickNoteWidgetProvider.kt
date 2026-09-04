package dev.cazimir.voicevault.quicknote

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.cazimir.voicevault.R

/** Single-tap launcher for [QuickNoteActivity] - the widget has no state of its own. */
class QuickNoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                Intent(context, QuickNoteActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val views = RemoteViews(context.packageName, R.layout.widget_quick_note).apply {
                setOnClickPendingIntent(R.id.quick_note_widget_icon, pendingIntent)
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
