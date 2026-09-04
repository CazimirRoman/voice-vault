package dev.cazimir.voicevault.vault

import android.content.Context
import dev.cazimir.voicevault.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A previously captured note, trimmed down for display (frontmatter stripped). */
data class NoteSummary(val captureId: String, val body: String)

/**
 * The vault's write surface. Two storage tiers on purpose:
 *
 * - Finished `.md` notes go into the user-selected folder over SAF ([VaultStorage]).
 * - A capture's `.wav` and its diagnostic `.log` wait in `context.filesDir/pending/` as
 *   plain files: whisper needs a real filesystem path, and orphan/partial audio must never
 *   appear in the user's synced vault. See the `migrate-vault-to-saf` design doc.
 *
 * Vault-touching methods take a [Context] and build a [SafVaultStorage] from it; `internal`
 * overloads take a [VaultStorage] directly so unit tests can pass a fake.
 */
object VaultWriter {

    private val FRONTMATTER = Regex("(?s)^---.*?---\\n")

    private fun storage(context: Context): VaultStorage = SafVaultStorage(context)

    // --- pending audio + diagnostics: app-internal filesDir ---

    fun pendingDir(context: Context): File = File(context.filesDir, Config.PENDING_AUDIO_DIR)

    fun writePendingAudio(context: Context, wavBytes: ByteArray, captureId: String): File {
        val dir = pendingDir(context).apply { mkdirs() }
        val file = File(dir, "$captureId.wav")
        AtomicFileWriter.write(file, wavBytes)
        return file
    }

    fun pendingAudioFiles(context: Context): List<File> =
        pendingDir(context).listFiles { file -> file.isFile && file.extension == "wav" }
            ?.toList()
            .orEmpty()

    /** Writes/overwrites the per-capture diagnostic record beside its orphan audio in `pending/`. */
    fun writeDiagnostic(context: Context, captureId: String, text: String) {
        val dir = pendingDir(context).apply { mkdirs() }
        AtomicFileWriter.write(diagnosticFile(dir, captureId), text)
    }

    /** Removes the diagnostic record for [captureId], if any - called wherever its audio is deleted. */
    fun deleteDiagnostic(context: Context, captureId: String) {
        diagnosticFile(pendingDir(context), captureId).delete()
    }

    private fun diagnosticFile(pendingDir: File, captureId: String): File =
        File(pendingDir, "$captureId.${Config.DIAGNOSTIC_RECORD_EXTENSION}")

    /** Recording duration in seconds, derived from file size given the fixed PCM16 mono header. */
    fun wavDurationSeconds(wavFile: File): Double {
        val dataBytes = (wavFile.length() - Config.WAV_HEADER_SIZE_BYTES).coerceAtLeast(0)
        return dataBytes / 2.0 / Config.SAMPLE_RATE_HZ
    }

    // --- vault notes: SAF ---

    /** Whether the persisted folder grant currently resolves to a writable folder. */
    fun vaultAccess(context: Context): VaultAccess = storage(context).access()

    fun isVaultReady(context: Context): Boolean = isVaultReady(storage(context))

    /** The selected folder described for the setup screen, or null when no valid grant is held. */
    fun vaultFolderInfo(context: Context): VaultFolderInfo? = storage(context).folderInfo()

    internal fun isVaultReady(storage: VaultStorage): Boolean =
        storage.access() is VaultAccess.Available

    /**
     * Matches the frontmatter schema already used across the vault's inbox notes
     * (Created / Priority / Area / Action), so voice notes sit alongside manually
     * written ones without looking like a different kind of thing.
     */
    fun writeNote(context: Context, text: String, captureId: String) =
        writeNote(storage(context), text, captureId)

    internal fun writeNote(storage: VaultStorage, text: String, captureId: String) {
        val createdDate = captureId.take(10)
        val content = "---\n" +
            "Created: $createdDate\n" +
            "Priority: \n" +
            "Area: \n" +
            "Action: false\n" +
            "---\n" +
            text
        storage.writeNote("$captureId.md", content)
    }

    /** Whether a note for [captureId] already exists - the source of truth for "did this capture succeed". */
    fun noteExists(context: Context, captureId: String): Boolean =
        noteExists(storage(context), captureId)

    internal fun noteExists(storage: VaultStorage, captureId: String): Boolean =
        storage.noteExists("$captureId.md")

    /** The most recently captured notes (voice or quick-text alike), newest first, frontmatter stripped. */
    fun recentNotes(context: Context, limit: Int = 5): List<NoteSummary> =
        recentNotes(storage(context), limit)

    internal fun recentNotes(storage: VaultStorage, limit: Int = 5): List<NoteSummary> =
        storage.listNotes()
            .sortedByDescending { it.lastModified }
            .take(limit)
            .map { note ->
                NoteSummary(
                    captureId = note.baseName,
                    body = note.content.replaceFirst(FRONTMATTER, "").trim()
                )
            }

    /** A timestamp-based id, disambiguated so it can never collide with an existing note or pending capture. */
    fun captureIdFor(context: Context, timestamp: Date = Date()): String =
        captureIdFor(storage(context), pendingDir(context), timestamp)

    internal fun captureIdFor(storage: VaultStorage, pendingDir: File, timestamp: Date = Date()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US)
        val base = formatter.format(timestamp)
        val takenNoteBases = storage.noteBaseNames().toHashSet()
        var candidate = base
        var suffix = 1
        while (candidate in takenNoteBases || File(pendingDir, "$candidate.wav").exists()) {
            suffix++
            candidate = "$base ($suffix)"
        }
        return candidate
    }
}
