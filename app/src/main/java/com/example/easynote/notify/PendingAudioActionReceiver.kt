package com.example.easynote.notify

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.easynote.Config
import java.io.File

/**
 * Backs the "Discard" action on a failure notification.
 *
 * Audio whisper finds no speech in fails identically on every attempt, so it would otherwise
 * sit in _pending/ forever and re-report itself on every later capture - the retry queue has
 * no other exit. The app still never deletes a recording on its own; this is the one path
 * where the user says the audio is not worth keeping.
 */
class PendingAudioActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DISCARD = "com.example.easynote.action.DISCARD_PENDING"
        const val EXTRA_CAPTURE_ID = "capture_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISCARD) return
        val captureId = intent.getStringExtra(EXTRA_CAPTURE_ID) ?: return

        // A failed delete needs no handling: the goal is only that the file stops being
        // picked up by the retry sweep, and it is already gone in that case.
        File(Config.pendingDir, "$captureId.wav").delete()

        val notificationId = intent.getIntExtra(
            EXTRA_NOTIFICATION_ID,
            CaptureNotifications.failureNotificationId(captureId)
        )
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }
}
