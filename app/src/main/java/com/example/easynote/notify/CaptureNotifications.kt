package com.example.easynote.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

        /** Failures that happened before any audio reached _pending/, so there is no file to key on. */
        private const val UNIDENTIFIED_FAILURE_NOTIFICATION_ID = 2

        /**
         * One stable id per capture. The retry sweep re-runs every pending file on every
         * capture, so a file that keeps failing re-posts its notification repeatedly - with
         * an id derived from the capture it *replaces* its own entry instead of stacking a
         * new one each time. Collides with [STATUS_NOTIFICATION_ID] only by coincidence,
         * which is cheap to rule out.
         */
        fun failureNotificationId(captureId: String): Int {
            val hash = captureId.hashCode()
            return if (hash == STATUS_NOTIFICATION_ID || hash == UNIDENTIFIED_FAILURE_NOTIFICATION_ID) {
                hash + 1
            } else {
                hash
            }
        }
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

    /**
     * @param captureId the pending audio this failure is about, or null if the capture died
     *   before anything was written and there is nothing to retry or discard.
     * @param allowDiscard whether to offer the discard action. False while the capture is
     *   only waiting on the one-time model download - that audio still transcribes on its
     *   own later, so offering to delete it would be offering to lose a good recording.
     */
    fun postFailure(message: String, captureId: String? = null, allowDiscard: Boolean = false) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId =
            captureId?.let { failureNotificationId(it) } ?: UNIDENTIFIED_FAILURE_NOTIFICATION_ID

        val builder = NotificationCompat.Builder(context, FAILURE_CHANNEL_ID)
            .setContentTitle("Note needs attention")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            // A re-post from the retry sweep must update silently. Without this, the stable
            // id stops the entries stacking but every capture would still re-buzz the shade.
            .setOnlyAlertOnce(true)
            // Deliberately not auto-cancelling: the alert has to outlive a stray tap for as
            // long as the audio is still sitting in _pending/. Discard, or a later successful
            // retry, are the only things that clear it.
            .setAutoCancel(false)

        if (captureId != null && allowDiscard) {
            builder.addAction(
                android.R.drawable.ic_menu_delete,
                "Discard",
                discardIntent(captureId, notificationId)
            )
        }

        manager.notify(notificationId, builder.build())
    }

    /** Clears the failure entry for [captureId] - used when a later retry finally succeeds. */
    fun cancelFailure(captureId: String) {
        context.getSystemService(NotificationManager::class.java)
            .cancel(failureNotificationId(captureId))
    }

    private fun discardIntent(captureId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, PendingAudioActionReceiver::class.java).apply {
            action = PendingAudioActionReceiver.ACTION_DISCARD
            putExtra(PendingAudioActionReceiver.EXTRA_CAPTURE_ID, captureId)
            putExtra(PendingAudioActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            // Per-capture request code: with a shared one, every button would inherit the
            // first capture's extras and discard the wrong file.
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun statusNotification(title: String): Notification =
        NotificationCompat.Builder(context, STATUS_CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
}
