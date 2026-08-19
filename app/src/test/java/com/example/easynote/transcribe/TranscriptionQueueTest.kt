package com.example.easynote.transcribe

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TranscriptionQueueTest {

    private class FakeTranscriber(private val text: String = "hello world") : Transcriber {
        var transcribeCalls = 0
        override suspend fun load() {}
        override suspend fun transcribe(wavFile: File): String {
            transcribeCalls++
            return text
        }
        override fun release() {}
    }

    @Test
    fun `file gone before its turn yields AlreadyHandled and the recognizer is never invoked`() = runBlocking {
        val wavFile = File.createTempFile("gone", ".wav")
        wavFile.delete()
        val transcriber = FakeTranscriber()

        val outcome = TranscriptionQueue.transcribe(wavFile) { transcriber }

        assertEquals(TranscriptionOutcome.AlreadyHandled, outcome)
        assertEquals(0, transcriber.transcribeCalls)
    }

    @Test
    fun `enqueueing the same path twice yields AlreadyHandled for the second`() = runBlocking {
        val wavFile = File.createTempFile("dupe", ".wav")
        try {
            val gate = CompletableDeferred<Unit>()
            val slowTranscriber = object : Transcriber {
                override suspend fun load() {}
                override suspend fun transcribe(wavFile: File): String {
                    gate.await()
                    return "[ Pause ]" // avoids a real vault write once unblocked
                }
                override fun release() {}
            }

            val first = launch { TranscriptionQueue.transcribe(wavFile) { slowTranscriber } }
            yield() // let the first entry register itself as in-flight and start waiting on the gate

            val second = TranscriptionQueue.transcribe(wavFile) { slowTranscriber }
            assertEquals(TranscriptionOutcome.AlreadyHandled, second)

            gate.complete(Unit)
            first.join()
        } finally {
            wavFile.delete()
        }
    }

    @Test
    fun `empty text and non-speech placeholders classify as NoSpeech`() = runBlocking {
        val placeholders = listOf(
            "", "[ Pause ]", "[Silence]", "[BLANK_AUDIO]", "[Music]", "[No speech]", "[Noise]", "[Inaudible]"
        )
        for (text in placeholders) {
            val wavFile = File.createTempFile("placeholder", ".wav")
            try {
                val outcome = TranscriptionQueue.transcribe(wavFile) { FakeTranscriber(text) }
                assertTrue("expected NoSpeech for \"$text\" but was $outcome", outcome is TranscriptionOutcome.NoSpeech)
                assertEquals(text.trim(), (outcome as TranscriptionOutcome.NoSpeech).rawText)
            } finally {
                wavFile.delete()
            }
        }
    }

    @Test
    fun `recognizer throwing classifies as TranscribeFailed`() = runBlocking {
        val wavFile = File.createTempFile("throws", ".wav")
        try {
            val boom = IllegalStateException("boom")
            val transcriber = object : Transcriber {
                override suspend fun load() {}
                override suspend fun transcribe(wavFile: File): String = throw boom
                override fun release() {}
            }

            val outcome = TranscriptionQueue.transcribe(wavFile) { transcriber }

            assertTrue(outcome is TranscriptionOutcome.TranscribeFailed)
            assertSame(boom, (outcome as TranscriptionOutcome.TranscribeFailed).cause)
        } finally {
            wavFile.delete()
        }
    }

    @Test
    fun `note write throwing classifies as NoteWriteFailed, distinct from a transcription error`() = runBlocking {
        val wavFile = File.createTempFile("notewrite", ".wav")
        // A plain file, not a directory - AtomicFileWriter's mkdirs()+write() then fails for real.
        val notADirectory = File.createTempFile("not-a-dir", "")
        try {
            val outcome = TranscriptionQueue.transcribe(wavFile, inboxDir = notADirectory) { FakeTranscriber() }

            assertTrue(outcome is TranscriptionOutcome.NoteWriteFailed)
        } finally {
            wavFile.delete()
            notADirectory.delete()
        }
    }
}
