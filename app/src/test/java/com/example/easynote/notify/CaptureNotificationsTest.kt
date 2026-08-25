package com.example.easynote.notify

import com.example.easynote.transcribe.TranscriptionOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureNotificationsTest {

    @Test
    fun `no-speech routes to the low-importance channel`() {
        assertEquals(
            CaptureNotifications.NO_SPEECH_CHANNEL_ID,
            CaptureNotifications.channelFor(TranscriptionOutcome.NoSpeech(""))
        )
    }

    @Test
    fun `genuine failures route to the high-importance failure channel`() {
        assertEquals(
            CaptureNotifications.FAILURE_CHANNEL_ID,
            CaptureNotifications.channelFor(TranscriptionOutcome.TranscribeFailed(RuntimeException()))
        )
        assertEquals(
            CaptureNotifications.FAILURE_CHANNEL_ID,
            CaptureNotifications.channelFor(TranscriptionOutcome.NoteWriteFailed(RuntimeException()))
        )
        assertEquals(
            CaptureNotifications.FAILURE_CHANNEL_ID,
            CaptureNotifications.channelFor(TranscriptionOutcome.ModelUnavailable(null))
        )
    }

    @Test
    fun `no-speech title names it as such and carries the capture id`() {
        assertEquals(
            "No speech · 2026-08-23 1417",
            CaptureNotifications.titleFor(TranscriptionOutcome.NoSpeech(""), "2026-08-23 1417")
        )
    }

    @Test
    fun `no-speech title survives a disambiguated capture id`() {
        assertEquals(
            "No speech · 2026-08-23 1417 (2)",
            CaptureNotifications.titleFor(TranscriptionOutcome.NoSpeech(""), "2026-08-23 1417 (2)")
        )
    }

    @Test
    fun `genuine failure titles say a note needs attention`() {
        assertEquals(
            "Note needs attention · 2026-08-23 1417",
            CaptureNotifications.titleFor(TranscriptionOutcome.TranscribeFailed(RuntimeException()), "2026-08-23 1417")
        )
        assertEquals(
            "Note needs attention · 2026-08-23 1417",
            CaptureNotifications.titleFor(TranscriptionOutcome.NoteWriteFailed(RuntimeException()), "2026-08-23 1417")
        )
        assertEquals(
            "Note needs attention · 2026-08-23 1417",
            CaptureNotifications.titleFor(TranscriptionOutcome.ModelUnavailable(null), "2026-08-23 1417")
        )
    }

    @Test
    fun `a null capture id falls back to the bare title`() {
        assertEquals(
            "Note needs attention",
            CaptureNotifications.titleFor(TranscriptionOutcome.TranscribeFailed(RuntimeException()), null)
        )
        assertEquals("Note needs attention", CaptureNotifications.genericTitle(null))
    }
}
