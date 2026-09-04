package dev.cazimir.voicevault.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class VaultWriterTest {

    /** In-memory [VaultStorage] so the note path is exercised without SAF or a real filesystem. */
    private class FakeVaultStorage(
        private val accessState: VaultAccess = VaultAccess.Available,
        private val seeded: List<RawNote> = emptyList()
    ) : VaultStorage {
        val written = linkedMapOf<String, String>()

        override fun access(): VaultAccess = accessState

        override fun folderInfo(): VaultFolderInfo? =
            if (accessState is VaultAccess.Available) VaultFolderInfo("Obsidian/00-Inbox", false) else null

        override fun writeNote(fileName: String, content: String) {
            if (accessState !is VaultAccess.Available) throw IOException("vault not accessible")
            written[fileName] = content
        }

        override fun noteExists(fileName: String): Boolean =
            fileName in written.keys || seeded.any { "${it.baseName}.md" == fileName }

        override fun noteBaseNames(): List<String> =
            (seeded.map { it.baseName } + written.keys.map { it.removeSuffix(".md") }).distinct()

        override fun listNotes(): List<RawNote> =
            seeded + written.map { (name, content) -> RawNote(name.removeSuffix(".md"), content, 0L) }
    }

    private fun timestampOf(value: String) =
        SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US).parse(value)!!

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
    fun `noteExists is true for a seeded note and for one just written, false otherwise`() {
        val storage = FakeVaultStorage(seeded = listOf(RawNote("2026-08-19 0900", "note", 1L)))

        assertTrue(VaultWriter.noteExists(storage, "2026-08-19 0900"))
        assertFalse(VaultWriter.noteExists(storage, "2026-08-19 1000"))

        VaultWriter.writeNote(storage, "body\n", "2026-08-19 1000")
        assertTrue(VaultWriter.noteExists(storage, "2026-08-19 1000"))
    }

    @Test
    fun `writeNote emits the shared inbox frontmatter schema around the body`() {
        val storage = FakeVaultStorage()

        VaultWriter.writeNote(storage, "hello there\n", "2026-08-19 0900")

        val content = storage.written.getValue("2026-08-19 0900.md")
        assertEquals(
            "---\nCreated: 2026-08-19\nPriority: \nArea: \nAction: false\n---\nhello there\n",
            content
        )
    }

    @Test
    fun `captureIdFor disambiguates against an existing note and existing pending audio`() {
        val pendingDir = createTempDir()
        try {
            val storage = FakeVaultStorage(seeded = listOf(RawNote("2026-08-19 0900", "x", 1L)))
            val ts = timestampOf("2026-08-19 0900")

            assertEquals("2026-08-19 0900 (2)", VaultWriter.captureIdFor(storage, pendingDir, ts))

            File(pendingDir, "2026-08-19 0900 (2).wav").createNewFile()
            assertEquals("2026-08-19 0900 (3)", VaultWriter.captureIdFor(storage, pendingDir, ts))
        } finally {
            pendingDir.deleteRecursively()
        }
    }

    @Test
    fun `recentNotes is newest first with frontmatter stripped`() {
        val storage = FakeVaultStorage(
            seeded = listOf(
                RawNote("older", "---\nCreated: 2026-01-01\n---\nold body", 100L),
                RawNote("newer", "---\nCreated: 2026-01-02\n---\nnew body", 200L)
            )
        )

        val recent = VaultWriter.recentNotes(storage, limit = 5)

        assertEquals(listOf("newer", "older"), recent.map { it.captureId })
        assertEquals("new body", recent.first().body)
    }

    @Test
    fun `isVaultReady is true only when the grant resolves to a writable folder`() {
        assertTrue(VaultWriter.isVaultReady(FakeVaultStorage(VaultAccess.Available)))
        assertFalse(VaultWriter.isVaultReady(FakeVaultStorage(VaultAccess.NotConfigured)))
        assertFalse(VaultWriter.isVaultReady(FakeVaultStorage(VaultAccess.Lost)))
    }

    @Test
    fun `writeNote through a lost grant throws, so the caller can raise the failure signal`() {
        try {
            VaultWriter.writeNote(FakeVaultStorage(VaultAccess.Lost), "body\n", "2026-08-19 0900")
            fail("expected writeNote to throw when the vault grant is lost")
        } catch (expected: IOException) {
            // the transcription queue turns this into NoteWriteFailed; capture start turns the
            // equivalent access() check into the 5-second vibration + persistent notification
        }
    }
}
