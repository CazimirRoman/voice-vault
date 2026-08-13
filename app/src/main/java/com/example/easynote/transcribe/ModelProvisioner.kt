package com.example.easynote.transcribe

import android.content.Context
import com.example.easynote.Config
import java.io.File
import java.io.IOException

/**
 * Owns the on-disk location and readiness of the whisper model in internal storage.
 * The bytes themselves arrive via [ModelDownloader]; whisper needs a real filesystem
 * path, so the model always lives as a plain file in `filesDir`.
 *
 * A separate `.complete` marker is what makes "ready" trustworthy: the model file
 * exists on disk while it is still being written, so its presence alone means nothing.
 */
object ModelProvisioner {

    fun modelFile(context: Context): File = File(context.filesDir, Config.MODEL_FILE_NAME)

    private fun markerFile(context: Context): File =
        File(context.filesDir, "${Config.MODEL_FILE_NAME}.complete")

    fun isModelReady(context: Context): Boolean =
        modelFile(context).exists() && markerFile(context).exists()

    /**
     * The model file, or an [IOException] if it has not been downloaded yet. Callers must
     * treat "not downloaded" as an ordinary failure - captures still record and stay in
     * `_pending/`, and are transcribed on a later retry once the download lands.
     */
    fun ensureModelFile(context: Context): File {
        if (!isModelReady(context)) {
            throw IOException("Speech model has not been downloaded yet")
        }
        return modelFile(context)
    }

    /** Clears a half-written model and any older one, so a download starts from clean state. */
    fun prepareForDownload(context: Context) {
        modelFile(context).delete()
        markerFile(context).delete()
        deleteStaleModels(context, keep = modelFile(context))
    }

    /** Written only after size and digest have been verified - see [ModelDownloader]. */
    fun markComplete(context: Context) {
        markerFile(context).writeText("ok")
    }

    /**
     * Swapping [Config.MODEL_FILE_NAME] would otherwise strand the previously downloaded
     * model in filesDir forever - these are ~57 MB each and nothing else ever deletes them.
     */
    private fun deleteStaleModels(context: Context, keep: File) {
        context.filesDir.listFiles()?.forEach { file ->
            val isModelArtifact = file.name.startsWith("ggml-") &&
                (file.name.endsWith(".bin") || file.name.endsWith(".bin.complete"))
            if (isModelArtifact && file.name != keep.name) {
                file.delete()
            }
        }
    }
}
