package com.example.easynote.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat

/**
 * The ongoing "listening / transcribing" notification required by the foreground
 * service, plus the persistent failure notification - the surface that survives
 * a missed 5-second warning vibration.
 */
class CaptureNotifications(private val context: Context) {

    companion object {
        const val STATUS_CHANNEL_ID = "capture_status"
        const val FAILURE_CHANNEL_ID = "capture_failures"
        const val STATUS_NOTIFICATION_ID = 1
    }

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL_ID, "Capture status", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(FAILURE_CHANNEL_ID, "Capture failures", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun listeningNotification(): Notification = statusNotification("Listening…")

    fun updateToTranscribing() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(STATUS_NOTIFICATION_ID, statusNotification("Transcribing…"))
    }

    fun postFailure(message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notification = NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
            .setContentTitle("Note needs attention")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setAutoCancel(true)
            .build()
        manager.notify(STATUS_NOTIFICATION_ID + System.currentTimeMillis().toInt(), notification)
    }

    private fun statusNotification(title: String): Notification =
        NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
}
