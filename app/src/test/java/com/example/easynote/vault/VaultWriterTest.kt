package com.example.easynote.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VaultWriterTest {

    @Test
    fun `wav duration is derived from file size given the 44-byte header`() {
        val sampleRateHz = 16_000
        val seconds = 3
        val file = File.createTempFile("duration", ".wav")
        try {
            file.writeBytes(ByteArray(44 + seconds * sampleRateHz * 2))
            assertEquals(seconds.toDouble(), VaultWriter.wavDurationSeconds(file), 0.001)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `noteExists is true only once the note file is present`() {
        val inboxDir = createTempDir()
        try {
            val captureId = "2026-08-19 0900"
            assertFalse(VaultWriter.noteExists(captureId, inboxDir))

            File(inboxDir, "$captureId.md").writeText("note")

            assertTrue(VaultWriter.noteExists(captureId, inboxDir))
        } finally {
            inboxDir.deleteRecursively()
        }
    }
}
