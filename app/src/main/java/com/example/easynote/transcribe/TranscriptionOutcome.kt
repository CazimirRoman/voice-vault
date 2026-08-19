package com.example.easynote.transcribe

import java.io.File

/**
 * What happened to one transcription attempt. Replaces a bare Boolean so that
 * "no speech in the audio" and "could not even attempt to transcribe it" are
 * distinguishable instead of collapsing into the same false.
 */
sealed interface TranscriptionOutcome {
    data class Success(val notePath: File) : TranscriptionOutcome

    /** The audio was gone by the time this entry reached the front of the queue - not a failure. */
    data object AlreadyHandled : TranscriptionOutcome

    /** Whisper ran and found nothing to transcribe; [rawText] is exactly what it returned. */
    data class NoSpeech(val rawText: String) : TranscriptionOutcome

    data class ModelUnavailable(val cause: Throwable?) : TranscriptionOutcome
    data class TranscribeFailed(val cause: Throwable) : TranscriptionOutcome
    data class NoteWriteFailed(val cause: Throwable) : TranscriptionOutcome
}
