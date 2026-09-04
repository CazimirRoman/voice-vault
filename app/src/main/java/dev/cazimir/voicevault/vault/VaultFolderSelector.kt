package dev.cazimir.voicevault.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import dev.cazimir.voicevault.Config

/**
 * Handles the result of the system `ACTION_OPEN_DOCUMENT_TREE` picker: takes a persistable
 * read/write grant on the chosen folder so it survives restarts and reboots, then records it
 * as the vault via [VaultGrant]. The picker itself is launched from an Activity through
 * `ActivityResultContracts.OpenDocumentTree`; this only owns what happens once a folder comes back.
 */
object VaultFolderSelector {

    /**
     * @return true if the grant was taken and stored; false if [treeUri] is null (user
     *   cancelled) or the persistable permission could not be taken.
     */
    fun persist(context: Context, treeUri: Uri?): Boolean {
        if (treeUri == null) return false
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(treeUri, flags)
            VaultGrant.save(context, treeUri)
            true
        } catch (t: Throwable) {
            Log.w(Config.LOG_TAG, "could not persist vault folder grant", t)
            false
        }
    }
}
