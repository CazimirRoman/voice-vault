package dev.cazimir.voicevault.transcribe

import java.io.File

/**
 * Isolates the whisper backend behind one interface. The MVP uses the prebuilt
 * whisper-android AAR; if it proves limiting, a vendored whisper.cpp build can
 * replace [WhisperTranscriber] without touching any caller.
 */
interface Transcriber {
    suspend fun load()
    suspend fun transcribe(wavFile: File): String
    fun release()
}
