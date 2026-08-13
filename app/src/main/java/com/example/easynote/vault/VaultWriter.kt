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
    fun writeNote(text: String, captureId: String): File {
        val file = File(Config.inboxDir, "$captureId.md")
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
