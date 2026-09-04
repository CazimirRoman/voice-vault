package dev.cazimir.voicevault.transcribe

import android.content.Context
import dev.cazimir.voicevault.Config
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** Progress of the one-time model download, observed by the setup screen. */
sealed interface ModelDownloadState {
    /** Not on disk and not being fetched - transcription cannot run yet. */
    data object Missing : ModelDownloadState
    data class Downloading(val fraction: Float) : ModelDownloadState
    data object Ready : ModelDownloadState
    data class Failed(val message: String) : ModelDownloadState
}

/**
 * Fetches the whisper model once, on first launch. A process-wide singleton rather than
 * anything Activity-scoped, so rotating or leaving the setup screen never restarts a
 * 57 MB transfer that is already half done.
 *
 * The download writes to a temp file and is only renamed into place after both the
 * expected byte count and the pinned SHA-256 match, so an interrupted or corrupted
 * transfer can never be mistaken for a usable model.
 */
object ModelDownloader {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Missing)
    val state: StateFlow<ModelDownloadState> = _state.asStateFlow()

    private var job: Job? = null

    /** Starts the download if needed. Safe to call repeatedly; concurrent calls are collapsed. */
    @Synchronized
    fun ensureDownloaded(context: Context) {
        val appContext = context.applicationContext
        if (ModelProvisioner.isModelReady(appContext)) {
            _state.value = ModelDownloadState.Ready
            return
        }
        if (job?.isActive == true) return
        _state.value = ModelDownloadState.Downloading(0f)
        job = scope.launch { runDownload(appContext) }
    }

    private suspend fun runDownload(context: Context) {
        val temp = File(context.filesDir, "${Config.MODEL_FILE_NAME}.download")
        try {
            ModelProvisioner.prepareForDownload(context)
            val digest = fetchTo(temp)
            verify(temp, digest)

            val target = ModelProvisioner.modelFile(context)
            if (!temp.renameTo(target)) {
                throw IOException("Could not move the downloaded model into place")
            }
            ModelProvisioner.markComplete(context)
            _state.value = ModelDownloadState.Ready
        } catch (cancellation: CancellationException) {
            temp.delete()
            _state.value = ModelDownloadState.Missing
            throw cancellation
        } catch (t: Throwable) {
            temp.delete()
            _state.value = ModelDownloadState.Failed(t.message ?: "Download failed")
        }
    }

    /** Streams the model into [temp], reporting progress, and returns its digest. */
    private suspend fun fetchTo(temp: File): ByteArray {
        val connection = (URL(Config.MODEL_DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Server returned HTTP ${connection.responseCode}")
            }
            val digest = MessageDigest.getInstance("SHA-256")
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var written = 0L
                    var reportedPercent = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        written += read

                        // Only on whole-percent changes: emitting per 64 KB chunk would
                        // recompose the setup screen ~900 times for no visible gain.
                        val percent = (written * 100 / Config.MODEL_SIZE_BYTES).toInt().coerceIn(0, 100)
                        if (percent != reportedPercent) {
                            reportedPercent = percent
                            _state.value = ModelDownloadState.Downloading(percent / 100f)
                        }
                    }
                }
            }
            return digest.digest()
        } finally {
            connection.disconnect()
        }
    }

    private fun verify(temp: File, digest: ByteArray) {
        if (temp.length() != Config.MODEL_SIZE_BYTES) {
            throw IOException("Download was incomplete (${temp.length()} of ${Config.MODEL_SIZE_BYTES} bytes)")
        }
        val hex = digest.joinToString("") { "%02x".format(it) }
        if (!hex.equals(Config.MODEL_SHA256, ignoreCase = true)) {
            throw IOException("Downloaded model failed its checksum check")
        }
    }
}
