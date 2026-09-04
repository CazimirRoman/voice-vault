package dev.cazimir.voicevault.transcribe

/**
 * What happened to one transcription attempt. Replaces a bare Boolean so that
 * "no speech in the audio" and "could not even attempt to transcribe it" are
 * distinguishable instead of collapsing into the same false.
 */
sealed interface TranscriptionOutcome {
    /** [noteName] is the base name (no `.md`) of the note written into the vault. */
    data class Success(val noteName: String) : TranscriptionOutcome

    /** The audio was gone by the time this entry reached the front of the queue - not a failure. */
    data object AlreadyHandled : TranscriptionOutcome

    /** Whisper ran and found nothing to transcribe; [rawText] is exactly what it returned. */
    data class NoSpeech(val rawText: String) : TranscriptionOutcome

    data class ModelUnavailable(val cause: Throwable?) : TranscriptionOutcome
    data class TranscribeFailed(val cause: Throwable) : TranscriptionOutcome
    data class NoteWriteFailed(val cause: Throwable) : TranscriptionOutcome
}
