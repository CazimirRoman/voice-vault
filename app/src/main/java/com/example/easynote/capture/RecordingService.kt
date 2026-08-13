package com.example.easynote.capture

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.example.easynote.notify.CaptureNotifications
import com.example.easynote.transcribe.ModelProvisioner
import com.example.easynote.transcribe.TranscriptionQueue
import com.example.easynote.vault.VaultWriter
import com.example.easynote.vault.WavEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Owns one capture end to end: acquire the microphone, signal via haptics, stop on
 * silence/tap/cap, persist audio before anything else, then hand off to transcription.
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
        startTapToStopOverlay()

        val result = try {
            audioRecorder.record(stop) { haptics.started() }
        } catch (t: Throwable) {
            CaptureController.activeStop = null
            CaptureEvents.notifyRecordingEnded()
            haptics.atRisk()
            notifications.postFailure("Could not start recording.")
            stopSelf(startId)
            return
        }

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
            haptics.atRisk()
            notifications.postFailure("Could not save the recording.")
            stopSelf(startId)
            return
        }

        notifications.updateToTranscribing()
        val success = TranscriptionQueue.transcribe(applicationContext, pendingFile)
        if (!success) {
            haptics.atRisk()
            notifications.postFailure(transcriptionFailureMessage())
        }
        stopSelf(startId)
    }

    /**
     * Until the one-time model download finishes, every capture fails transcription. That is
     * recoverable - the audio is already in _pending/ and gets retried - but only if the user
     * is told what to actually do about it.
     */
    private fun transcriptionFailureMessage(): String =
        if (!ModelProvisioner.isModelReady(this)) {
            "Speech model not downloaded yet. Open EasyNote to finish setup - audio is safe in Inbox/_pending."
        } else {
            "Transcription failed. Audio kept in Inbox/_pending."
        }

    private suspend fun retryPending(startId: Int) {
        // Retrying without a model can only fail, once per pending file - which would bury
        // the user in failure notifications during the first-launch download.
        if (!VaultWriter.isVaultReady() || !ModelProvisioner.isModelReady(this)) {
            if (startId >= 0) stopSelf(startId)
            return
        }
        for (file in VaultWriter.pendingAudioFiles()) {
            val success = TranscriptionQueue.transcribe(applicationContext, file)
            if (!success) {
                haptics.atRisk()
                notifications.postFailure("Retry failed for ${file.name}.")
            }
        }
        if (startId >= 0) stopSelf(startId)
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
