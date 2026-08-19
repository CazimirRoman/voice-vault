package com.example.easynote.transcribe

import android.content.Context
import android.util.Log
import com.example.easynote.Config
import com.example.easynote.vault.VaultWriter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Serializes access to a single loaded whisper model across overlapping captures.
 * A capture in progress never has to wait for the app's UI - it only ever waits
 * behind other pending transcriptions.
 */
object TranscriptionQueue {

    private val mutex = Mutex()
    private var transcriber: Transcriber? = null
    private val pendingCount = AtomicInteger(0)

    /** Canonical paths currently enqueued or running, so a sweep can never double-enqueue a file. */
    private val inFlight: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Starts loading the model without waiting for it, so load time overlaps recording.
     * Failures are swallowed here on purpose - this runs in a fire-and-forget coroutine
     * where a throw would take the process down, and [transcribe] reports the same
     * failure later on a path that can actually surface it to the user.
     */
    suspend fun preload(context: Context) {
        try {
            mutex.withLock { ensureLoaded { loadWhisper(context) } }
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "model preload failed", t)
        }
    }

    /** Transcribes [wavFile] into a vault note, or classifies why it could not be. */
    suspend fun transcribe(context: Context, wavFile: File): TranscriptionOutcome =
        transcribe(wavFile, Config.inboxDir) { loadWhisper(context) }

    /**
     * Test seam: [loadTranscriber] replaces the real whisper-android load so outcome
     * classification can be exercised without an Android [Context], and [inboxDir] replaces
     * the real vault location so a forced note-write failure never touches the real filesystem.
     */
    internal suspend fun transcribe(
        wavFile: File,
        inboxDir: File = Config.inboxDir,
        loadTranscriber: suspend () -> Transcriber
    ): TranscriptionOutcome {
        val canonicalPath = wavFile.canonicalPath
        if (!inFlight.add(canonicalPath)) {
            return TranscriptionOutcome.AlreadyHandled
        }
        pendingCount.incrementAndGet()
        return try {
            mutex.withLock {
                // The file may have been transcribed or discarded by another queue entry while
                // this one waited for the lock - re-check existence now, not just at enqueue time.
                if (!wavFile.exists()) {
                    return@withLock TranscriptionOutcome.AlreadyHandled
                }
                // Before the one-time model download finishes this fails for every capture,
                // which must stay recoverable - the audio then waits in _pending/ and is
                // picked up by a later retry scan.
                val loaded = try {
                    ensureLoaded(loadTranscriber)
                } catch (t: Throwable) {
                    Log.w(Config.LOG_TAG, "model load failed for ${wavFile.name}", t)
                    return@withLock TranscriptionOutcome.ModelUnavailable(t)
                }
                processOne(loaded, wavFile, inboxDir)
            }
        } finally {
            inFlight.remove(canonicalPath)
            if (pendingCount.decrementAndGet() == 0) {
                mutex.withLock {
                    transcriber?.release()
                    transcriber = null
                }
            }
        }
    }

    private suspend fun loadWhisper(context: Context): Transcriber {
        val created = WhisperTranscriber(context.applicationContext)
        created.load()
        return created
    }

    private suspend fun ensureLoaded(loadTranscriber: suspend () -> Transcriber): Transcriber {
        transcriber?.let { return it }
        val created = loadTranscriber()
        transcriber = created
        return created
    }

    private suspend fun processOne(transcriber: Transcriber, wavFile: File, inboxDir: File): TranscriptionOutcome {
        val text = try {
            transcriber.transcribe(wavFile).trim()
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "transcription failed for ${wavFile.name}", t)
            return TranscriptionOutcome.TranscribeFailed(t)
        }
        if (text.isEmpty() || isNonSpeechPlaceholder(text)) {
            return TranscriptionOutcome.NoSpeech(text)
        }
        val captureId = wavFile.nameWithoutExtension
        val noteFile = try {
            VaultWriter.writeNote(text + "\n", captureId, inboxDir)
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "note write failed for $captureId", t)
            return TranscriptionOutcome.NoteWriteFailed(t)
        }
        wavFile.delete()
        VaultWriter.deleteDiagnostic(captureId)
        return TranscriptionOutcome.Success(noteFile)
    }

    /**
     * Near-silent audio doesn't come back as an empty string - whisper describes it with a
     * bracketed placeholder instead (confirmed on device: a room-noise-only capture returned
     * literally "[ Pause ]"). Treat those the same as no real speech.
     */
    private fun isNonSpeechPlaceholder(text: String): Boolean {
        val normalized = text.trim()
        if (!normalized.startsWith("[") || !normalized.endsWith("]")) return false
        val inner = normalized.substring(1, normalized.length - 1).trim().lowercase()
        return inner in NON_SPEECH_TOKENS
    }

    private val NON_SPEECH_TOKENS = setOf(
        "pause", "silence", "blank_audio", "music", "no speech", "noise", "inaudible"
    )
}
