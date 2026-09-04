package dev.cazimir.voicevault.vault

import android.content.Context
import android.provider.DocumentsContract
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import dev.cazimir.voicevault.Config
import java.io.IOException

/**
 * [VaultStorage] over the Storage Access Framework: the user-selected inbox folder, reached
 * through the persisted tree URI in [VaultGrant]. Resolution is attempted on every call so a
 * grant that goes away mid-session surfaces as [VaultAccess.Lost] rather than a stale handle.
 *
 * Scoped to a folder on local/shared device storage (see the change's Non-Goals) - that is
 * where an Obsidian vault lives, and where `DocumentsContract.renameDocument` behaves like a
 * filesystem rename.
 */
class SafVaultStorage(context: Context) : VaultStorage {

    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    /** Resolved once per instance; instances are created per [VaultWriter] call and short-lived. */
    private val inbox: DocumentFile? by lazy { resolveInbox() }

    private fun resolveInbox(): DocumentFile? {
        val treeUri = VaultGrant.stored(appContext) ?: return null
        return try {
            DocumentFile.fromTreeUri(appContext, treeUri)?.takeIf { it.isDirectory && it.canWrite() }
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "vault tree URI did not resolve", t)
            null
        }
    }

    override fun access(): VaultAccess = when {
        VaultGrant.stored(appContext) == null -> VaultAccess.NotConfigured
        inbox == null -> VaultAccess.Lost
        else -> VaultAccess.Available
    }

    override fun folderInfo(): VaultFolderInfo? {
        val dir = inbox ?: return null
        val treeUri = VaultGrant.stored(appContext) ?: return null

        // Tree document id is "<volume>:<relative/path>", e.g. "primary:Obsidian/.../00-Inbox".
        val docId = try {
            DocumentsContract.getTreeDocumentId(treeUri)
        } catch (t: Throwable) {
            null
        }
        val displayPath = docId?.substringAfter(':', "")?.takeIf { it.isNotBlank() }
            ?: dir.name
            ?: "selected folder"

        val looksLikeVaultRoot = dir.listFiles().any { it.isDirectory && it.name == ".obsidian" }

        return VaultFolderInfo(displayPath, looksLikeVaultRoot)
    }

    override fun writeNote(fileName: String, content: String) {
        val dir = inbox ?: throw IOException("vault folder is not accessible")
        val base = fileName.removeSuffix(".md")
        val partialName = "$base.md.partial"

        // renameDocument dedupes a name collision to "name (1)" instead of overwriting, and a
        // leftover partial from a crashed write would do the same - clear both first.
        dir.findFile(partialName)?.delete()
        dir.findFile(fileName)?.delete()

        val partial = dir.createFile("application/octet-stream", partialName)
            ?: throw IOException("could not create $partialName in vault")
        try {
            resolver.openOutputStream(partial.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
                out.flush()
            } ?: throw IOException("could not open $partialName for writing")

            val renamed = DocumentsContract.renameDocument(resolver, partial.uri, fileName)
            // Some providers return null on a rename that in fact succeeded - confirm by lookup.
            if (renamed == null && dir.findFile(fileName)?.isFile != true) {
                throw IOException("could not rename $partialName to $fileName")
            }
        } catch (t: Throwable) {
            partial.delete()
            throw t
        }
    }

    override fun noteExists(fileName: String): Boolean =
        inbox?.findFile(fileName)?.isFile == true

    override fun noteBaseNames(): List<String> =
        noteFiles().mapNotNull { it.name?.removeSuffix(".md") }

    override fun listNotes(): List<RawNote> =
        noteFiles().mapNotNull { file ->
            val name = file.name?.removeSuffix(".md") ?: return@mapNotNull null
            val text = try {
                resolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            } catch (t: Throwable) {
                Log.w(Config.LOG_TAG, "could not read note ${file.name}", t)
                null
            } ?: return@mapNotNull null
            RawNote(baseName = name, content = text, lastModified = file.lastModified())
        }

    private fun noteFiles(): List<DocumentFile> =
        inbox?.listFiles()?.filter { it.isFile && it.name?.endsWith(".md") == true }.orEmpty()
}
