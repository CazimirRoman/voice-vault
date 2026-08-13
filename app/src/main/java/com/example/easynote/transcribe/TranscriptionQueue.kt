package com.example.easynote.transcribe

import android.content.Context
import com.example.easynote.vault.VaultWriter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
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

    /**
     * Starts loading the model without waiting for it, so load time overlaps recording.
     * Failures are swallowed here on purpose - this runs in a fire-and-forget coroutine
     * where a throw would take the process down, and [transcribe] reports the same
     * failure later on a path that can actually surface it to the user.
     */
    suspend fun preload(context: Context) {
        try {
            mutex.withLock { ensureLoaded(context) }
        } catch (t: Throwable) {
            // Reported by transcribe(); nothing to do here.
        }
    }

    /** Transcribes [wavFile] into a vault note. Returns false on any failure; audio is untouched then. */
    suspend fun transcribe(context: Context, wavFile: File): Boolean {
        pendingCount.incrementAndGet()
        return try {
            mutex.withLock {
                // Before the one-time model download finishes this fails for every capture,
                // which must stay an ordinary "false" - the audio then waits in _pending/
                // and is picked up by a later retry scan.
                val loaded = try {
                    ensureLoaded(context)
                } catch (t: Throwable) {
                    return@withLock false
                }
                processOne(loaded, wavFile)
            }
        } finally {
            if (pendingCount.decrementAndGet() == 0) {
                mutex.withLock {
                    transcriber?.release()
                    transcriber = null
                }
            }
        }
    }

    private suspend fun ensureLoaded(context: Context): Transcriber {
        transcriber?.let { return it }
        val created = WhisperTranscriber(context.applicationContext)
        created.load()
        transcriber = created
        return created
    }

    private suspend fun processOne(transcriber: Transcriber, wavFile: File): Boolean {
        return try {
            val text = transcriber.transcribe(wavFile).trim()
            if (text.isEmpty() || isNonSpeechPlaceholder(text)) {
                return false
            }
            val captureId = wavFile.nameWithoutExtension
            VaultWriter.writeNote(text + "\n", captureId)
            wavFile.delete()
            true
        } catch (t: Throwable) {
            false
        }
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
