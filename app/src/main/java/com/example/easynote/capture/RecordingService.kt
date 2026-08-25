package com.example.easynote.capture

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.util.Log
import com.example.easynote.Config
import com.example.easynote.notify.CaptureNotifications
import com.example.easynote.transcribe.ModelProvisioner
import com.example.easynote.transcribe.TranscriptionOutcome
import com.example.easynote.transcribe.TranscriptionQueue
import com.example.easynote.vault.VaultWriter
import com.example.easynote.vault.WavEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Owns one capture end to end: acquire the microphone, signal via haptics, stop on
 * silence/tap/screen-off/cap, persist audio before anything else, then hand off to transcription.
 * Runs as a foreground service so recording survives the screen locking or the
 * phone being pocketed - the whole point of an eyes-free capture flow.
 */
class RecordingService : Service() {

    companion object {
        const val ACTION_START_CAPTURE = "com.example.easynote.action.START_CAPTURE"
        const val ACTION_RETRY_PENDING = "com.example.easynote.action.RETRY_PENDING"
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var haptics: Haptics
    private lateinit var notifications: CaptureNotifications
    private val audioRecorder = AudioRecorder()

    override fun onCreate() {
        super.onCreate()
        haptics = Haptics(this)
        notifications = CaptureNotifications(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RETRY_PENDING -> serviceScope.launch { retryPending(startId) }
            else -> serviceScope.launch { runCapture(startId) }
        }
        return START_NOT_STICKY
    }

    private suspend fun runCapture(startId: Int) {
        startForeground(CaptureNotifications.STATUS_NOTIFICATION_ID, notifications.listeningNotification())

        if (!VaultWriter.isVaultReady()) {
            haptics.atRisk()
            notifications.postFailure("Vault is not accessible. Check storage permission and vault folder.")
            stopSelf(startId)
            return
        }

        serviceScope.launch { TranscriptionQueue.preload(applicationContext) }
        serviceScope.launch { retryPending(startId = -1) }

        val stop = ExternalStop()
        CaptureController.activeStop = stop
        // Per-capture, not a service field: captures can overlap, and each one must stop
        // on its own screen-off rather than sharing a registration with another.
        val screenOffStop = registerScreenOffStop(stop)
        startTapToStopOverlay()

        val result = try {
            audioRecorder.record(stop) { haptics.started() }
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "recording failed to start", t)
            screenOffStop.release()
            CaptureController.activeStop = null
            CaptureEvents.notifyRecordingEnded()
            haptics.atRisk()
            notifications.postFailure("Could not start recording.")
            stopSelf(startId)
            return
        }

        screenOffStop.release()
        CaptureController.activeStop = null
        haptics.stopped()
        CaptureEvents.notifyRecordingEnded()

        if (result.pcm.isEmpty()) {
            haptics.atRisk()
            notifications.postFailure("No audio was captured.")
            stopSelf(startId)
            return
        }

        val wav = WavEncoder.encode(result.pcm, result.sampleRateHz)
        val captureId = VaultWriter.captureIdFor()
        val pendingFile = try {
            VaultWriter.writePendingAudio(wav, captureId)
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "could not save recording for $captureId", t)
            haptics.atRisk()
            notifications.postFailure("Could not save the recording.")
            stopSelf(startId)
            return
        }

        notifications.updateToTranscribing()
        val outcome = TranscriptionQueue.transcribe(applicationContext, pendingFile)
        handleOutcome(outcome, captureId)
        stopSelf(startId)
    }

    private suspend fun retryPending(startId: Int) {
        // Retrying without a model can only fail, once per pending file - which would bury
        // the user in failure notifications during the first-launch download.
        if (!VaultWriter.isVaultReady() || !ModelProvisioner.isModelReady(this)) {
            if (startId >= 0) stopSelf(startId)
            return
        }
        val pendingFiles = VaultWriter.pendingAudioFiles()
        // A failure notification only ever gets cleared when a later sweep finds and
        // transcribes its file - clear anything whose file is no longer pending at all,
        // including the three phantom alerts a broken build already left behind.
        notifications.clearStaleFailures(pendingFiles.mapTo(mutableSetOf()) { it.nameWithoutExtension })
        for (file in pendingFiles) {
            val captureId = file.nameWithoutExtension
            val outcome = TranscriptionQueue.transcribe(applicationContext, file)
            handleOutcome(outcome, captureId)
        }
        if (startId >= 0) stopSelf(startId)
    }

    /** Reports (or silently clears) a classified transcription outcome for one capture. */
    private fun handleOutcome(outcome: TranscriptionOutcome, captureId: String) {
        if (outcome is TranscriptionOutcome.Success || outcome is TranscriptionOutcome.AlreadyHandled) {
            notifications.cancelFailure(captureId)
            return
        }
        // Belt-and-braces against the class of bug, not just the one race that caused it:
        // if a note is already there by any path, this capture did not fail.
        if (VaultWriter.noteExists(captureId)) {
            notifications.cancelFailure(captureId)
            return
        }

        VaultWriter.writeDiagnostic(captureId, diagnosticText(outcome, captureId))
        val durationSeconds = VaultWriter.wavDurationSeconds(File(Config.pendingDir, "$captureId.wav"))
        val allowDiscard = outcome !is TranscriptionOutcome.ModelUnavailable
        notifications.postFailure(outcome, captureId, durationSeconds, allowDiscard)

        // The at-risk rumble means recorded speech is at risk of being lost. NoSpeech means
        // there was no speech to lose, so it never buzzes - not on the first attempt, and
        // not on any later retry sweep. Every other failure class always buzzes.
        if (outcome !is TranscriptionOutcome.NoSpeech) {
            haptics.atRisk()
        }
    }

    private fun diagnosticText(outcome: TranscriptionOutcome, captureId: String): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val detail = when (outcome) {
            is TranscriptionOutcome.NoSpeech ->
                "No speech detected. Whisper output: \"${outcome.rawText}\""
            is TranscriptionOutcome.ModelUnavailable ->
                "Speech model unavailable.\n${outcome.cause?.stackTraceToString().orEmpty()}"
            is TranscriptionOutcome.TranscribeFailed ->
                "Transcription error.\n${outcome.cause.stackTraceToString()}"
            is TranscriptionOutcome.NoteWriteFailed ->
                "Note write error.\n${outcome.cause.stackTraceToString()}"
            is TranscriptionOutcome.Success, TranscriptionOutcome.AlreadyHandled ->
                "" // handled before this is ever called
        }
        return "$timestamp  $captureId\n$detail\n"
    }

    /**
     * Ends the capture when the user turns off the screen. Pressing the power button and
     * pocketing the phone is the real "I'm done" gesture, and it is also what fills the
     * microphone with fabric noise - so on the app's primary flow the silence threshold is
     * never reached. This is a stop, not a cancel: it sets the same flag tap-to-stop uses,
     * so everything downstream of the recording loop is unchanged.
     *
     * Owned by the service rather than the overlay, so it still works if the overlay never
     * launched - nothing in the capture flow may depend on that activity.
     * `ACTION_SCREEN_OFF` is not deliverable to a manifest-declared receiver.
     */
    private fun registerScreenOffStop(stop: ExternalStop): Registration {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_OFF) stop.request()
            }
        }
        return try {
            registerReceiver(receiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            Registration { unregisterReceiver(receiver) }
        } catch (t: Throwable) {
            // A capture that cannot listen for screen-off is still a valid capture; it just
            // falls back to the silence and hard-cap stop conditions.
            Log.w(Config.LOG_TAG, "could not listen for screen-off", t)
            Registration { }
        }
    }

    /** Idempotent teardown - `unregisterReceiver` throws if it is called a second time. */
    private class Registration(private val undo: () -> Unit) {
        private var released = false

        fun release() {
            if (released) return
            released = true
            undo()
        }
    }

    private fun startTapToStopOverlay() {
        val intent = Intent(this, TapToStopActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        startActivity(intent)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
