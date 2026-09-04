package dev.cazimir.voicevault.vault

import java.io.File
import java.io.IOException

/**
 * Writes to a temp file and renames into place, so a sync client (Obsidian Sync,
 * Syncthing) watching the vault never observes a partially written file.
 */
object AtomicFileWriter {

    fun write(targetFile: File, bytes: ByteArray) {
        targetFile.parentFile?.mkdirs()
        val tempFile = File(targetFile.parentFile, "${targetFile.name}.tmp")
        tempFile.writeBytes(bytes)
        if (!tempFile.renameTo(targetFile)) {
            throw IOException("Failed to rename ${tempFile.name} to ${targetFile.name}")
        }
    }

    fun write(targetFile: File, text: String) = write(targetFile, text.toByteArray(Charsets.UTF_8))
}
