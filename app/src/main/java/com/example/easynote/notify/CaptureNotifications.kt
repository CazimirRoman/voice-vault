package com.example.easynote.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.easynote.transcribe.TranscriptionOutcome

/**
 * The ongoing "listening / transcribing" notification required by the foreground
 * service, plus the persistent failure notification - the surface that survives
 * a missed 5-second warning vibration.
 */
class CaptureNotifications(private val context: Context) {

    companion object {
        const val STATUS_CHANNEL_ID = "capture_status"
        const val FAILURE_CHANNEL_ID = "capture_failures"
        const val NO_SPEECH_CHANNEL_ID = "capture_no_speech"
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

        /**
         * Which channel a classified outcome alerts on. `NoSpeech` is the one outcome that
         * risks no recorded speech, so it alone gets the silent channel; every other failure
         * keeps the loud one. Kept free of [Context] so it is unit-testable on the JVM.
         */
        fun channelFor(outcome: TranscriptionOutcome): String = when (outcome) {
            is TranscriptionOutcome.NoSpeech -> NO_SPEECH_CHANNEL_ID
            is TranscriptionOutcome.ModelUnavailable,
            is TranscriptionOutcome.TranscribeFailed,
            is TranscriptionOutcome.NoteWriteFailed -> FAILURE_CHANNEL_ID
            is TranscriptionOutcome.Success, TranscriptionOutcome.AlreadyHandled ->
                error("$outcome is not a failure and should never reach a notification")
        }

        /** The generic, non-outcome-specific title, used by the string-only [postFailure] overload. */
        fun genericTitle(captureId: String?): String =
            captureId?.let { "Note needs attention · $it" } ?: "Note needs attention"

        /** Which title a classified outcome's notification carries. Also [Context]-free. */
        fun titleFor(outcome: TranscriptionOutcome, captureId: String?): String =
            if (outcome is TranscriptionOutcome.NoSpeech) {
                captureId?.let { "No speech · $it" } ?: "No speech"
            } else {
                genericTitle(captureId)
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
        manager.createNotificationChannel(
            NotificationChannel(
                NO_SPEECH_CHANNEL_ID,
                "Captures with no speech",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    fun listeningNotification(): Notification = statusNotification("Listening…")

    fun updateToTranscribing() {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(STATUS_NOTIFICATION_ID, statusNotification("Transcribing…"))
    }

    /** Posts a failure whose cause is a classified [TranscriptionOutcome], with a per-outcome message. */
    fun postFailure(
        outcome: TranscriptionOutcome,
        captureId: String,
        durationSeconds: Double,
        allowDiscard: Boolean
    ) {
        postFailure(
            channelId = channelFor(outcome),
            title = titleFor(outcome, captureId),
            message = messageFor(outcome, durationSeconds),
            captureId = captureId,
            allowDiscard = allowDiscard
        )
    }

    private fun messageFor(outcome: TranscriptionOutcome, durationSeconds: Double): String {
        val duration = formatDuration(durationSeconds)
        return when (outcome) {
            is TranscriptionOutcome.NoSpeech -> {
                val heard = outcome.rawText.ifBlank { "nothing" }
                "No speech detected in a $duration recording. Whisper heard: \"$heard\". " +
                    "Discard it if it is not worth keeping."
            }
            is TranscriptionOutcome.ModelUnavailable ->
                "Speech model not downloaded yet. Open EasyNote to finish setup - " +
                    "a $duration recording is safe in Inbox/_pending."
            is TranscriptionOutcome.TranscribeFailed ->
                "Transcription failed for a $duration recording. Audio kept in Inbox/_pending."
            is TranscriptionOutcome.NoteWriteFailed ->
                "Transcribed a $duration recording but could not save the note. Audio kept in Inbox/_pending."
            is TranscriptionOutcome.Success, TranscriptionOutcome.AlreadyHandled ->
                error("$outcome is not a failure and should never reach a notification")
        }
    }

    private fun formatDuration(seconds: Double): String {
        val totalSeconds = seconds.toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        return if (minutes > 0) "%d:%02d".format(minutes, secs) else "${secs}s"
    }

    /**
     * @param captureId the pending audio this failure is about, or null if the capture died
     *   before anything was written and there is nothing to retry or discard.
     * @param allowDiscard whether to offer the discard action. False while the capture is
     *   only waiting on the one-time model download - that audio still transcribes on its
     *   own later, so offering to delete it would be offering to lose a good recording.
     */
    fun postFailure(message: String, captureId: String? = null, allowDiscard: Boolean = false) {
        postFailure(
            channelId = FAILURE_CHANNEL_ID,
            title = genericTitle(captureId),
            message = message,
            captureId = captureId,
            allowDiscard = allowDiscard
        )
    }

    private fun postFailure(
        channelId: String,
        title: String,
        message: String,
        captureId: String?,
        allowDiscard: Boolean
    ) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val notificationId =
            captureId?.let { failureNotificationId(it) } ?: UNIDENTIFIED_FAILURE_NOTIFICATION_ID

        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
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

    /**
     * Cancels any active failure notification whose capture is not in [expectedCaptureIds].
     * A failure notification only ever gets cleared by a later sweep that finds and
     * transcribes its file - once the file is gone by any other path (success elsewhere,
     * Discard, a stale build's phantom alert), nothing would otherwise ever look at it again.
     */
    fun clearStaleFailures(expectedCaptureIds: Set<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val expectedIds = expectedCaptureIds.mapTo(mutableSetOf()) { failureNotificationId(it) }
        for (active in manager.activeNotifications) {
            if (active.notification.channelId !in setOf(FAILURE_CHANNEL_ID, NO_SPEECH_CHANNEL_ID)) continue
            if (active.id == UNIDENTIFIED_FAILURE_NOTIFICATION_ID) continue
            if (active.id !in expectedIds) {
                manager.cancel(active.id)
            }
        }
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
