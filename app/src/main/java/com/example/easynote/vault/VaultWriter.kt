package com.example.easynote.vault

import android.os.Environment
import com.example.easynote.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object VaultWriter {

    fun hasStorageAccess(): Boolean = Environment.isExternalStorageManager()

    fun isVaultReady(): Boolean = hasStorageAccess() && Config.inboxDir.exists()

    fun writePendingAudio(wavBytes: ByteArray, captureId: String): File {
        Config.pendingDir.mkdirs()
        val file = File(Config.pendingDir, "$captureId.wav")
        AtomicFileWriter.write(file, wavBytes)
        return file
    }

    /**
     * Matches the frontmatter schema already used across the vault's inbox notes
     * (Created / Priority / Area / Action), so voice notes sit alongside manually
     * written ones without looking like a different kind of thing.
     */
    fun writeNote(text: String, captureId: String, inboxDir: File = Config.inboxDir): File {
        val file = File(inboxDir, "$captureId.md")
        val createdDate = captureId.take(10)
        val content = "---\n" +
            "Created: $createdDate\n" +
            "Priority: \n" +
            "Area: \n" +
            "Action: false\n" +
            "---\n" +
            text
        AtomicFileWriter.write(file, content)
        return file
    }

    fun pendingAudioFiles(): List<File> =
        Config.pendingDir.listFiles { file -> file.isFile && file.extension == "wav" }
            ?.toList()
            .orEmpty()

    /** Writes/overwrites the per-capture diagnostic record beside its orphan audio in `_pending/`. */
    fun writeDiagnostic(captureId: String, text: String, pendingDir: File = Config.pendingDir) {
        pendingDir.mkdirs()
        AtomicFileWriter.write(diagnosticFile(captureId, pendingDir), text)
    }

    /** Removes the diagnostic record for [captureId], if any - called wherever its audio is deleted. */
    fun deleteDiagnostic(captureId: String, pendingDir: File = Config.pendingDir) {
        diagnosticFile(captureId, pendingDir).delete()
    }

    private fun diagnosticFile(captureId: String, pendingDir: File): File =
        File(pendingDir, "$captureId.${Config.DIAGNOSTIC_RECORD_EXTENSION}")

    /** Whether a note for [captureId] already exists - the source of truth for "did this capture succeed". */
    fun noteExists(captureId: String, inboxDir: File = Config.inboxDir): Boolean =
        File(inboxDir, "$captureId.md").exists()

    /** Recording duration in seconds, derived from file size given the fixed PCM16 mono header. */
    fun wavDurationSeconds(wavFile: File): Double {
        val dataBytes = (wavFile.length() - Config.WAV_HEADER_SIZE_BYTES).coerceAtLeast(0)
        return dataBytes / 2.0 / Config.SAMPLE_RATE_HZ
    }

    /** A timestamp-based id, disambiguated so it can never collide with an existing note. */
    fun captureIdFor(timestamp: Date = Date()): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HHmm", Locale.US)
        val base = formatter.format(timestamp)
        var candidate = base
        var suffix = 1
        while (File(Config.inboxDir, "$candidate.md").exists() ||
            File(Config.pendingDir, "$candidate.wav").exists()
        ) {
            suffix++
            candidate = "$base ($suffix)"
        }
        return candidate
    }
}
