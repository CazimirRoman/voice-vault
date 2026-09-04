package dev.cazimir.voicevault.vault

import android.content.Context
import android.net.Uri

/**
 * The single persisted setting this app has: the `content://` tree URI of the vault
 * folder the user picked once via the system folder picker. Stored as a string in a
 * small `SharedPreferences` file; resolved back to a `DocumentFile` on every access.
 *
 * There is no `.complete`-marker dance here (unlike [dev.cazimir.voicevault.transcribe.ModelProvisioner]):
 * this is one small key write, not a large download that can be observed half-finished.
 */
object VaultGrant {

    private const val PREFS = "vault_grant"
    private const val KEY_TREE_URI = "tree_uri"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The stored tree URI, or null if the user has never picked a folder. */
    fun stored(context: Context): Uri? =
        prefs(context).getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun save(context: Context, treeUri: Uri) {
        prefs(context).edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_TREE_URI).apply()
    }
}
